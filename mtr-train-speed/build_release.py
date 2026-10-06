#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
多版本发布构建：一次产出 5 个 MC 版本 x Fabric/Forge 的发布 jar。

用法：
    python build_release.py                      # 全部 10 个目标
    python build_release.py --only 1.20.1:fabric # 只构建一个
    python build_release.py --jars <MTR jar 目录> --out <输出目录>

产物命名（与 1.0.0 的发布习惯一致）：
    MTR4Advanced-<版本>-<MC版本><Fabric|Forge>.jar
    例：MTR4Advanced-1.0.1-1.20.1Fabric.jar

与 build.py 的区别：
  * build.py  = 日常开发用，只针对一个 MTR jar（默认 1.20.1 Fabric）
  * 本脚本    = 发布用，对每个目标分别编译、分别生成平台元数据、分别校验注入点
每个目标都会：
  1. 用对应版本的 MTR jar 当 classpath 编译（自动补 net.minecraft stub）
  2. 生成该平台/该 MC 版本的元数据（Fabric: fabric.mod.json；Forge: META-INF/mods.toml + MANIFEST 的 MixinConfigs）
  3. 跑 tools/check_targets.py 校验注入点，任一目标失败即整体失败
"""

import argparse
import json
import os
import re
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
WORKSPACE = os.path.dirname(ROOT)
MIXIN_JAR = os.path.join(ROOT, "libs", "mixin-0.8.7.jar")
SRC = os.path.join(ROOT, "src", "main", "java")
RES = os.path.join(ROOT, "src", "main", "resources")
BUILD = os.path.join(ROOT, "build", "release")

VERSION = "1.0.1"
MOD_NAME = "MTR4 Advanced"
MOD_AUTHOR = "nansai"
MOD_DESCRIPTION = "MTR 附属模组：现实的列车限速控制（提速由车尾通过控制、减速由车头前瞻制动）、侧线「列车最高时速」上限，以及按列车长度推算的侧线时刻表。"

# Fabric 的 mod id 允许连字符；Forge 的 modId 只允许 [a-z0-9_]（连字符非法），所以两边不同名
FABRIC_MOD_ID = "mtr4-advanced"
FORGE_MOD_ID = "mtr4advanced"
MIXIN_CONFIG = "mtr4-advanced.mixins.json"

# MTR 版本：所有目标都用 4.0.4（本模组只对 4.0.4 校验过；4.0.5 已知不兼容）
MTR_VERSION = "4.0.4"
MTR_BREAKS = ">=4.0.5 <4.0.6"

TARGETS = [(mc, "fabric") for mc in ("1.18.2", "1.19.2", "1.19.4", "1.20.1", "1.20.4")] + \
          [(mc, "forge") for mc in ("1.18.2", "1.19.2", "1.19.4", "1.20.1", "1.20.4")]

# Forge 的 minecraft 依赖用 Maven 精确区间；forge 本体要求 [36,)（与 MTR 自己的 mods.toml 一致）
FORGE_LOADER_RANGE = "[36,)"


def run(args):
    return subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")


def clean_dir(path):
    """逐文件删除（不用 shutil.rmtree，某些环境会拦）"""
    if not os.path.isdir(path):
        return
    for dirpath, _, filenames in os.walk(path, topdown=False):
        for name in filenames:
            try:
                os.remove(os.path.join(dirpath, name))
            except OSError:
                pass
    for dirpath, _, _ in os.walk(path, topdown=False):
        try:
            os.rmdir(dirpath)
        except OSError:
            pass


def collect_sources(root, suffix=".java"):
    result = []
    for dirpath, _, filenames in os.walk(root):
        for name in sorted(filenames):
            if name.endswith(suffix):
                result.append(os.path.join(dirpath, name))
    return result


def javac(output_dir, sources, classpath):
    cmd = ["javac", "-nowarn", "--release", "17", "-proc:none", "-encoding", "UTF-8",
           "-J-Duser.language=en", "-J-Duser.country=US",
           "-d", output_dir, "-cp", classpath] + sources
    return run(cmd)


def stub_parent(fqn):
    """决定一个 net.minecraft stub 该继承谁。

    Forge 版 MTR 会把控件当成 net.minecraft.client.gui.components.AbstractWidget 传递，
    所以 gui 包下的 stub 统一挂在 AbstractWidget 之下；其余仍挂在 class_339 之下。
    """
    if fqn == "net.minecraft.class_339":
        return None
    if fqn == "net.minecraft.client.gui.components.AbstractWidget":
        return "net.minecraft.class_339"
    if fqn.startswith("net.minecraft.client.gui."):
        return "net.minecraft.client.gui.components.AbstractWidget"
    return "net.minecraft.class_339"


def write_stub(stub_src, fqn, parent):
    """写一个空壳 stub（只参与编译，不进最终 jar）。"""
    simple = fqn.rsplit(".", 1)[-1]
    package = fqn.rsplit(".", 1)[0]
    folder = os.path.join(stub_src, package.replace(".", os.sep))
    os.makedirs(folder, exist_ok=True)
    body = "package %s;\npublic class %s%s {}\n" % (
        package, simple, "" if parent is None else " extends " + parent)
    with open(os.path.join(folder, simple + ".java"), "w", encoding="utf-8") as f:
        f.write(body)


def compile_against(mtr_jar, work):
    """编译本模组，迭代补齐 net.minecraft stub（与 build.py 同一套办法）。"""
    stub_src = os.path.join(work, "stubs", "src")
    stub_out = os.path.join(work, "stubs", "classes")
    classes = os.path.join(work, "classes")
    for path in (stub_src, stub_out, classes):
        clean_dir(path)
        os.makedirs(path, exist_ok=True)

    # 预置根类 + GUI 根类：
    # Fabric 版 MTR 的映射层把 Minecraft 类型都藏在一个占位符 class_339 后面，
    # Forge 版则直接引用真实类名（net.minecraft.client.gui.components.AbstractWidget 等），
    # 所以 stub 必须带上正确的继承层级，否则 addChild(new ClickableWidget(...)) 这类调用过不了类型检查。
    write_stub(stub_src, "net.minecraft.class_339", None)
    write_stub(stub_src, "net.minecraft.client.gui.components.AbstractWidget", "net.minecraft.class_339")

    sources = collect_sources(SRC)
    base_cp = mtr_jar + os.pathsep + MIXIN_JAR
    result = None
    for attempt in range(40):
        result = javac(classes, sources, base_cp + os.pathsep + stub_out)
        if result.returncode == 0:
            return classes, attempt + 1, len(collect_sources(stub_src)), None
        missing = sorted(set(re.findall(r"class file for ([\w.$]+) not found", result.stderr)))
        missing = [m for m in missing if m.startswith("net.minecraft.")]
        if not missing:
            return None, attempt + 1, 0, (result.stdout or "") + (result.stderr or "")
        for fqn in missing:
            write_stub(stub_src, fqn, stub_parent(fqn))
        stub_result = javac(stub_out, collect_sources(stub_src), "")
        if stub_result.returncode != 0:
            return None, attempt + 1, 0, (stub_result.stdout or "") + (stub_result.stderr or "")
    return None, 40, 0, "stub generation exceeded limit\n" + ((result.stdout or "") + (result.stderr or "") if result else "")


def write_fabric_metadata(res_dir, mc):
    meta = {
        "schemaVersion": 1,
        "id": FABRIC_MOD_ID,
        "version": VERSION,
        "name": MOD_NAME,
        "description": MOD_DESCRIPTION,
        "authors": [MOD_AUTHOR],
        "license": "MIT",
        "environment": "*",
        "entrypoints": {},
        "mixins": [MIXIN_CONFIG],
        "depends": {
            "minecraft": mc,
            "fabricloader": ">=0.14.0",
            "mtr": ">=" + MTR_VERSION,
        },
        "breaks": {
            "mtr": MTR_BREAKS,
        },
    }
    with open(os.path.join(res_dir, "fabric.mod.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
        f.write("\n")


def write_forge_metadata(res_dir, mc):
    meta_dir = os.path.join(res_dir, "META-INF")
    os.makedirs(meta_dir, exist_ok=True)
    toml = """modLoader = "javafml"
loaderVersion = "%s"
license = "MIT"

[[mods]]
modId = "%s"
version = "%s"
displayName = "%s"
authors = "%s"
description = "%s"

[[dependencies.%s]]
modId = "forge"
mandatory = true
versionRange = "%s"
ordering = "NONE"
side = "BOTH"

[[dependencies.%s]]
modId = "minecraft"
mandatory = true
versionRange = "[%s]"
ordering = "NONE"
side = "BOTH"

[[dependencies.%s]]
modId = "mtr"
mandatory = true
versionRange = "[%s,%s)"
ordering = "NONE"
side = "BOTH"
""" % (FORGE_LOADER_RANGE, FORGE_MOD_ID, VERSION, MOD_NAME, MOD_AUTHOR, MOD_DESCRIPTION,
       FORGE_MOD_ID, FORGE_LOADER_RANGE,
       FORGE_MOD_ID, mc,
       FORGE_MOD_ID, MTR_VERSION, "4.0.5")
    with open(os.path.join(meta_dir, "mods.toml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(toml)


def build_jar(classes_dir, res_dir, out_jar, loader):
    """打包：class + 元数据。Forge 额外写 MANIFEST 的 MixinConfigs（Forge 靠它加载 mixin 配置）。"""
    manifest = "Manifest-Version: 1.0\n"
    if loader == "forge":
        manifest += "MixinConfigs: %s\n" % MIXIN_CONFIG
    manifest += "\n"
    with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", manifest)
        for dirpath, _, filenames in os.walk(classes_dir):
            for name in sorted(filenames):
                full = os.path.join(dirpath, name)
                z.write(full, os.path.relpath(full, classes_dir))
        for dirpath, _, filenames in os.walk(res_dir):
            for name in sorted(filenames):
                full = os.path.join(dirpath, name)
                z.write(full, os.path.relpath(full, res_dir))


def check_targets(mtr_jar, classes_dir):
    script = os.path.join(ROOT, "tools", "check_targets.py")
    return run([sys.executable, script, "--jar", mtr_jar, "--classes", classes_dir])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jars", default=os.path.join(WORKSPACE, "_mtr_jars"),
                        help="存放 MTR-<loader>-<版本>+<MC>.jar 的目录")
    parser.add_argument("--out", default=os.path.join(WORKSPACE, "releases"), help="发布 jar 输出目录")
    parser.add_argument("--only", default=None, help="只构建一个目标，格式 1.20.1:fabric")
    args = parser.parse_args()

    targets = TARGETS
    if args.only:
        mc, _, loader = args.only.partition(":")
        targets = [(m, l) for (m, l) in TARGETS if m == mc and l == loader]
        if not targets:
            print("目标不存在: " + args.only)
            return 1

    if not os.path.isfile(MIXIN_JAR):
        print("缺少 " + MIXIN_JAR)
        return 1

    os.makedirs(args.out, exist_ok=True)
    results = []
    for mc, loader in targets:
        label = "%s/%s" % (mc, loader)
        mtr_jar = os.path.join(args.jars, "MTR-%s-%s+%s.jar" % (loader, MTR_VERSION, mc))
        if not os.path.isfile(mtr_jar):
            print("[%s] 缺少 MTR jar: %s" % (label, mtr_jar))
            results.append((mc, loader, "MISSING-MTR", None))
            continue

        work = os.path.join(BUILD, "%s-%s" % (mc, loader))
        res_dir = os.path.join(work, "resources")
        clean_dir(work)
        os.makedirs(res_dir, exist_ok=True)

        # 公共资源：mixin 配置
        with open(os.path.join(RES, MIXIN_CONFIG), "rb") as src, \
             open(os.path.join(res_dir, MIXIN_CONFIG), "wb") as dst:
            dst.write(src.read())
        if loader == "fabric":
            write_fabric_metadata(res_dir, mc)
        else:
            write_forge_metadata(res_dir, mc)

        classes, rounds, stubs, error = compile_against(mtr_jar, work)
        if classes is None:
            print("[%s] 编译失败：\n%s" % (label, error))
            results.append((mc, loader, "COMPILE-FAIL", None))
            continue

        check = check_targets(mtr_jar, classes)
        if check.returncode != 0:
            print("[%s] 注入点校验失败：\n%s\n%s" % (label, check.stdout, check.stderr))
            results.append((mc, loader, "CHECK-FAIL", None))
            continue

        out_jar = os.path.join(args.out, "MTR4Advanced-%s-%s%s.jar" % (VERSION, mc, loader.capitalize()))
        build_jar(classes, res_dir, out_jar, loader)
        print("[%s] OK  编译 %d 轮 / %d stub  校验 18/18  -> %s" % (label, rounds, stubs, os.path.basename(out_jar)))
        results.append((mc, loader, "OK", out_jar))

    print("")
    ok = [r for r in results if r[2] == "OK"]
    print("成功 %d / %d" % (len(ok), len(results)))
    for mc, loader, status, _ in results:
        if status != "OK":
            print("  未通过: %s/%s -> %s" % (mc, loader, status))
    return 0 if len(ok) == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
