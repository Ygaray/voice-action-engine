#!/usr/bin/env python3
"""Release gate 15: the published coordinates in a maven-local carry the tag, and the core dependency matches the manifest.

usage: published_versions.py <m2> <group> <tag> <manifest>

For every module of the manifest (scripts/modules.list, 5 whitespace-separated columns:
name packaging artifactId kotlinPackage dependsOnCore):
  - the POM exists and its project version is <tag>;
  - the .module exists and its component version is <tag>;
  - dependsOnCore == yes: the POM depends on voice-action-engine-core at <tag>, and every .module dependency on core is at <tag>;
  - dependsOnCore == no and the module is not core: neither the POM nor the .module depends on voice-action-engine-core.

Each problem goes to stderr as "  <module>: <problem>". Exit 0 when there is none, 1 otherwise. Standard library only.
"""
import json
import os
import sys
import xml.etree.ElementTree as ET

CORE = "voice-action-engine-core"


def read_manifest(path):
    rows = []
    with open(path, encoding="utf-8") as fh:
        for raw in fh:
            fields = raw.split("#", 1)[0].split()
            if not fields:
                continue
            if len(fields) != 5:
                sys.stderr.write("  manifest: expected 5 fields, got %d in '%s'\n" % (len(fields), raw.strip()))
                sys.exit(2)
            rows.append(fields)
    return rows


def check_module(m2, group, tag, name, artifact, needs_core):
    bad = []
    d = os.path.join(m2, *group.split("."), artifact, tag)
    pom = os.path.join(d, "%s-%s.pom" % (artifact, tag))
    mod = os.path.join(d, "%s-%s.module" % (artifact, tag))
    if not os.path.isfile(pom):
        bad.append("%s: POM %s-%s.pom is missing" % (name, artifact, tag))
    else:
        root = ET.parse(pom).getroot()
        ns = root.tag[: root.tag.index("}") + 1] if root.tag.startswith("{") else ""
        ver = root.find(ns + "version")
        if ver is None or (ver.text or "").strip() != tag:
            bad.append("%s: the POM project version is not %s" % (name, tag))
        deps = [x for x in root.iter(ns + "dependency") if (x.findtext(ns + "artifactId") or "").strip() == CORE]
        if needs_core:
            if not deps:
                bad.append("%s: the POM does not depend on %s" % (name, CORE))
            for x in deps:
                if (x.findtext(ns + "version") or "").strip() != tag:
                    bad.append("%s: the POM depends on %s at a version other than %s" % (name, CORE, tag))
        elif name != "core" and deps:
            bad.append("%s: the POM depends on %s but the manifest says dependsOnCore=no" % (name, CORE))
    if not os.path.isfile(mod):
        bad.append("%s: %s-%s.module is missing" % (name, artifact, tag))
        return bad
    doc = json.load(open(mod, encoding="utf-8"))
    if doc.get("component", {}).get("version") != tag:
        bad.append("%s: the .module component version is not %s" % (name, tag))
    for variant in doc.get("variants", []):
        for dep in variant.get("dependencies", []):
            if dep.get("module") != CORE:
                continue
            if needs_core:
                if dep.get("version", {}).get("requires") != tag:
                    bad.append("%s: a .module dependency on %s is not at %s" % (name, CORE, tag))
            elif name != "core":
                bad.append("%s: the .module depends on %s but the manifest says dependsOnCore=no" % (name, CORE))
    return bad


def main(argv):
    if len(argv) != 4:
        sys.stderr.write("usage: published_versions.py <m2> <group> <tag> <manifest>\n")
        return 2
    m2, group, tag, manifest = argv
    bad = []
    for name, _packaging, artifact, _pkg, core in read_manifest(manifest):
        bad.extend(check_module(m2, group, tag, name, artifact, core == "yes"))
    for b in bad:
        sys.stderr.write("  " + b + "\n")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
