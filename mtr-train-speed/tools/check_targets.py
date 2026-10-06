#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
离线校验 mixin 注入点（不需要 Knot / Minecraft 运行时）。

原理：
  1. javap -v 读编译好的 mixin class，抓出 @Inject / @Redirect 里的
     method=... 和 @At(target=...) 字符串
  2. javap -p 读 MTR 官方 jar 里的目标类，比对同名方法是否存在

用法：
    python tools/check_targets.py
    python tools/check_targets.py --jar 路径/到/MTR.jar --classes build/classes
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys

DEFAULT_JAR = r"D:\wrkspce\mtrtrainspeed\[A]MTR-fabric-4.0.4.jar"

# mixin 类 -> 目标类（javap -v 的注解输出里 Mixin#value 不好解析，这里直接列出来）
MIXIN_TARGETS = {
    "cn.nansai.mtr4advanced.mixin.SidingMixin": "org.mtr.core.data.Siding",
    "cn.nansai.mtr4advanced.mixin.SidingSchemaMixin": "org.mtr.core.generated.data.SidingSchema",
    "cn.nansai.mtr4advanced.mixin.SidingTimeSegmentsMixin": "org.mtr.core.data.Siding",
    "cn.nansai.mtr4advanced.mixin.VehicleSpeedMixin": "org.mtr.core.data.Vehicle",
    "cn.nansai.mtr4advanced.mixin.client.SidingScreenMixin": "org.mtr.mod.screen.SidingScreen",
    "cn.nansai.mtr4advanced.mixin.access.VehicleAccess": "org.mtr.core.data.Vehicle",
    "cn.nansai.mtr4advanced.mixin.access.VehicleSchemaAccess": "org.mtr.core.generated.data.VehicleSchema",
    "cn.nansai.mtr4advanced.mixin.access.SavedRailScreenBaseAccess": "org.mtr.mod.screen.SavedRailScreenBase",
}

sig_cache = {}


def jdk_version_of(path):
    """'C:\\Program Files\\Java\\jdk-21' -> 21；'...\\jdk1.8.0_202' -> 8"""
    m = re.search(r"jdk-?(\d+(?:\.\d+)*)", os.path.basename(path), re.I)
    if not m:
        return 0
    parts = m.group(1).split(".")
    major = int(parts[0])
    if major == 1 and len(parts) > 1:      # 1.8.0_202 这种老命名
        major = int(parts[1])
    return major


def resolve_tool(name, explicit):
    """定位 javap。

    PATH 上常常只有 Oracle 的 javapath（java / javac），**没有 javap**，
    且 JAVA_HOME 也未必设置 → 裸跑 `javap` 会 FileNotFoundError。
    顺序：显式参数 > PATH > JAVA_HOME > 常见 JDK 安装目录（版本高的优先）。
    """
    if explicit != name:          # 用户显式指定过，别自作聪明
        return explicit
    found = shutil.which(name)
    if found:
        return found
    java_home = os.environ.get("JAVA_HOME") or ""
    if java_home:
        candidate = os.path.join(java_home, "bin", name + ".exe" if os.name == "nt" else name)
        if os.path.isfile(candidate):
            return candidate
    if os.name == "nt":
        roots = [r"C:\Program Files\Java", r"C:\Program Files\Eclipse Adoptium",
                 r"C:\Program Files\Microsoft", r"C:\Program Files (x86)\Java"]
        pattern = "jdk*"
    else:
        roots = ["/usr/lib/jvm", "/opt/java", "/opt/jdk"]
        pattern = "*jdk*"
    hits = []
    for root in roots:
        hits += [d for d in glob.glob(os.path.join(root, pattern)) if os.path.isdir(d)]
    # 目录名是 jdk-21 / jdk-17 / jdk1.8.0_202，直接反序字符串排序会把 jdk1.8 排到最前，
    # javap 8 读不了 release 17 的 class（静默返回空）→ 必须按真实版本号降序
    hits.sort(key=jdk_version_of, reverse=True)
    for d in hits:
        exe = os.path.join(d, "bin", name + ".exe" if os.name == "nt" else name)
        if os.path.isfile(exe):
            return exe
    return explicit


def javap(exe, *args):
    return subprocess.run([exe] + list(args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def members(exe, jar, fqcn):
    """目标类的成员集合，元素形如 'foo(D)V' 或 'bar'（字段）"""
    if fqcn in sig_cache:
        return sig_cache[fqcn]
    result = javap(exe, "-p", "-classpath", jar, fqcn)
    found = set()
    for raw in result.stdout.splitlines():
        line = raw.strip().rstrip(";")
        m = re.match(r"^(?:public|private|protected|static|final|abstract|\s)*\s*(.+?)\s+(\w+)\((.*)\)$", line)
        if m:
            found.add(m.group(2) + "(" + m.group(3) + ")")
            continue
        m = re.match(r"^(?:public|private|protected|static|final|\s)*\s*([\w.\[\]<>]+)\s+(\w+)$", line)
        if m:
            found.add(m.group(2))
    sig_cache[fqcn] = found
    return found


def strip_value(value):
    return value.strip().strip('[]" ')


def call_site_count(exe, jar, target, host_method, member):
    """
    数目标方法在【宿主方法内部】被调用了几次。
    @Redirect 没写 ordinal 时必须是 1 —— 否则 MTR 改版后新增调用点会静默命中错误位置。

    必须限定在宿主方法里数：整个类数会算进别的方法（例如 Siding#getUpcomingSlowerSpeed
    内部也调了 PathData#getSpeedLimitMetersPerMillisecond，但那不是我们的注入点）。
    """
    result = javap(exe, "-p", "-c", "-classpath", jar, target)
    if result.returncode != 0:
        return -1
    # javap 对同类方法写 "Method foo:(...)V"，跨类写 "Method org/mtr/.../Foo.bar:(...)V"
    pattern = re.compile(r"// Method (?:[\w/$]+\.)?" + re.escape(member) + r"[:(]")
    count = 0
    in_host = False
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip())
        if indent == 2 and line.rstrip().endswith(";"):
            # 方法/字段声明行：只有恰好是宿主方法时才开始计数
            in_host = (host_method + "(") in line
            continue
        if in_host and pattern.search(line):
            count += 1
    return count


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=DEFAULT_JAR)
    parser.add_argument("--classes", default=os.path.join(here, "..", "build", "classes"))
    parser.add_argument("--javap", default="javap")
    args = parser.parse_args()
    args.javap = resolve_tool("javap", args.javap)

    jar = os.path.abspath(args.jar)
    classes = os.path.abspath(args.classes)
    if not os.path.isfile(jar):
        print("找不到 MTR jar: " + jar)
        return 1
    if not os.path.isdir(classes):
        print("找不到编译输出目录（先跑 build.py）: " + classes)
        return 1

    ok = True
    checked = 0
    for root, _, files in os.walk(classes):
        for name in sorted(files):
            if not name.endswith(".class"):
                continue
            path = os.path.join(root, name)
            fq = os.path.relpath(path, classes).replace(os.sep, ".")[:-6]
            target = MIXIN_TARGETS.get(fq)
            if not target:
                continue
            verbose = javap(args.javap, "-p", "-v", "-classpath", classes, fq)
            host_method = None
            for raw in verbose.stdout.splitlines():
                line = raw.strip()
                if line.startswith("method="):
                    method = strip_value(line.split("=", 1)[1])
                    host_method = method
                    hit = any(x.startswith(method + "(") for x in members(args.javap, jar, target))
                    checked += 1
                    ok = ok and hit
                    print("  %s  %s -> %s#%s" % ("OK  " if hit else "MISS", fq, target, method))
                elif line.startswith("target="):
                    desc = strip_value(line.split("=", 1)[1])
                    m = re.match(r"L([\w/$]+);([\w$<>]+)(\(.*?\))?(.*)", desc)
                    if not m:
                        continue
                    owner = m.group(1).replace("/", ".")
                    method = m.group(2)
                    hit = any(x.startswith(method + "(") for x in members(args.javap, jar, owner))
                    checked += 1
                    ok = ok and hit
                    print("  %s  %s -> %s#%s" % ("OK  " if hit else "MISS", fq, owner, method + (m.group(3) or "")))
                    if not hit or host_method is None:
                        continue
                    sites = call_site_count(args.javap, jar, target, host_method, method)
                    if sites < 0:
                        continue
                    checked += 1
                    unique = (sites == 1)
                    ok = ok and unique
                    # owner 是被调用方法所在的类，target 只是宿主类（调用点所在的类），别混用
                    print("  %s  调用点 %d 处（%s#%s）" % ("OK  " if unique else "WARN", sites, owner, method))

    print("")
    print(("ALL OK (%d 项检查)" % checked) if ok else "有检查项没过，Mixin 运行时可能抛 InvalidInjectionException 或静默命中错误位置")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
