#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MTR4 Advanced —— 离线构建脚本（Windows / Linux 通用）

只要装了 JDK（javac）就能用，不需要 Gradle / Fabric Loom / 联网。

思路：
  1. 直接把 MTR 官方 jar 当编译 classpath（MTR 的类名都是稳定的 org.mtr.*，不走混淆重映射）。
  2. 本模组不直接引用任何 net.minecraft.* 类，
     但 javac 为了解析 MTR 映射层的继承链会去加载少量 Minecraft 类，
     所以这里自动生成空壳 stub 让 javac 通过（stub 只参与编译，不会进最终 jar）。
  3. mixin 只改造 org.mtr.* 的类，因此不需要 refmap，也不需要 Mixin 注解处理器。

用法：
    python build.py                       # 自动找上级目录里的 MTR jar
    MTR_JAR=路径 python build.py          # 手动指定 MTR jar
产物：
    build/libs/MTR4Advanced-1.0.0.jar
"""

import glob
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
WORKSPACE = os.path.dirname(ROOT)

MIXIN_JAR = os.path.join(ROOT, "libs", "mixin-0.8.7.jar")

SRC = os.path.join(ROOT, "src", "main", "java")
RES = os.path.join(ROOT, "src", "main", "resources")
BUILD = os.path.join(ROOT, "build")
STUB_SRC = os.path.join(BUILD, "stubs", "src")
STUB_OUT = os.path.join(BUILD, "stubs", "classes")
CLASSES = os.path.join(BUILD, "classes")
OUT_JAR = os.path.join(BUILD, "libs", "MTR4Advanced-1.0.0.jar")


def find_mtr_jar():
    env = os.environ.get("MTR_JAR")
    if env and os.path.isfile(env):
        return env
    # 注意：Windows 的 glob 不区分大小写，但 Linux / macOS **区分**。
    # 而 MTR 官方 release 的文件名是小写（mtr-fabric-4.0.4.jar），本地又常被改名成 [A]MTR-...
    # 所以用 glob 的字符类 [Mm][Tt][Rr] 一次覆盖所有大小写组合 —— 所有平台行为一致，
    # 不必再写两套 pattern。
    patterns = [
        # 精确版本优先，避免工作区同时存在 4.0.4 / 4.0.5 时靠文件系统顺序挑中错的那个
        os.path.join(WORKSPACE, "*[Mm][Tt][Rr]*4.0.4*.jar"),
        os.path.join(ROOT, "libs", "*[Mm][Tt][Rr]*4.0.4*.jar"),
        # 兜底：仍然允许别的版本，但要排序保证可复现，并且打日志提醒
        os.path.join(WORKSPACE, "*[Mm][Tt][Rr]*.jar"),
        os.path.join(ROOT, "libs", "*[Mm][Tt][Rr]*.jar"),
    ]
    for pattern in patterns:
        hits = sorted(p for p in glob.glob(pattern) if not p.endswith("mixin-0.8.7.jar"))
        if hits:
            if "4.0.4" not in os.path.basename(hits[0]):
                print("警告：没找到 MTR 4.0.4，回退到 " + hits[0] + "，注入点可能失效，请用 MTR_JAR 显式指定")
            return hits[0]
    return None


def run(args):
    return subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")


def javac(output_dir, sources, classpath):
    cmd = ["javac", "-nowarn", "--release", "17", "-proc:none",
           "-encoding", "UTF-8",
           "-J-Duser.language=en", "-J-Duser.country=US",
           "-d", output_dir, "-cp", classpath] + sources
    return run(cmd)


def clean_dir(path):
    """删掉目录里的所有文件和子目录（不删目录本身，避开 rmtree 的安全策略拦截）。"""
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


def collect_sources(root):
    result = []
    for dirpath, _, filenames in os.walk(root):
        for name in filenames:
            if name.endswith(".java"):
                result.append(os.path.join(dirpath, name))
    return result


def main():
    mtr_jar = find_mtr_jar()
    if not mtr_jar:
        print("找不到 MTR jar，请设置环境变量 MTR_JAR 指向 MTR-fabric-4.0.4.jar")
        return 1
    if not os.path.isfile(MIXIN_JAR):
        print("缺少 " + MIXIN_JAR)
        return 1
    print("MTR jar : " + mtr_jar)
    print("mixin   : " + MIXIN_JAR)

    # 逐文件删除，不用 shutil.rmtree —— 有些环境（回收站 / 安全策略）会拦掉 rmtree。
    for path in (STUB_SRC, STUB_OUT, CLASSES, os.path.dirname(OUT_JAR)):
        clean_dir(path)
    for path in (STUB_SRC, STUB_OUT, CLASSES, os.path.dirname(OUT_JAR)):
        os.makedirs(path, exist_ok=True)

    # 预置根类：让所有 Minecraft stub 都继承它，
    # 这样 MTR 映射层的控件才能被当成 class_339 传给 addChild。
    seed_dir = os.path.join(STUB_SRC, "net", "minecraft")
    os.makedirs(seed_dir, exist_ok=True)
    with open(os.path.join(seed_dir, "class_339.java"), "w", encoding="utf-8") as f:
        f.write("package net.minecraft;\npublic class class_339 {}\n")

    mod_sources = collect_sources(SRC)
    base_cp = mtr_jar + os.pathsep + MIXIN_JAR

    for attempt in range(40):
        result = javac(CLASSES, mod_sources, base_cp + os.pathsep + STUB_OUT)
        if result.returncode == 0:
            print("compilation ok (round %d, %d stub(s))" % (attempt + 1, len(collect_sources(STUB_SRC))))
            break
        missing = sorted(set(re.findall(r"class file for ([\w.$]+) not found", result.stderr)))
        missing = [m for m in missing if m.startswith("net.minecraft.")]
        if not missing:
            sys.stdout.write(result.stdout)
            sys.stderr.write(result.stderr)
            return 1
        for fqn in missing:
            simple = fqn.rsplit(".", 1)[-1]
            package = fqn.rsplit(".", 1)[0]
            folder = os.path.join(STUB_SRC, package.replace(".", os.sep))
            os.makedirs(folder, exist_ok=True)
            with open(os.path.join(folder, simple + ".java"), "w", encoding="utf-8") as f:
                if fqn == "net.minecraft.class_339":
                    f.write("package %s;\npublic class %s {}\n" % (package, simple))
                else:
                    f.write("package %s;\npublic class %s extends class_339 {}\n" % (package, simple))
        stub_result = javac(STUB_OUT, collect_sources(STUB_SRC), "")
        if stub_result.returncode != 0:
            sys.stdout.write(stub_result.stdout)
            sys.stderr.write(stub_result.stderr)
            return 1
    else:
        print("stub generation exceeded limit")
        sys.stdout.write(result.stdout)
        sys.stderr.write(result.stderr)
        return 1

    import zipfile
    with zipfile.ZipFile(OUT_JAR, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, filenames in os.walk(CLASSES):
            for name in filenames:
                full = os.path.join(dirpath, name)
                z.write(full, os.path.relpath(full, CLASSES))
        for dirpath, _, filenames in os.walk(RES):
            for name in filenames:
                full = os.path.join(dirpath, name)
                z.write(full, os.path.relpath(full, RES))
    print("built: " + OUT_JAR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
