#!/usr/bin/env python3
"""Reset only the isolated test database, then run the packaged app on port 8081."""

import base64
import os
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def read_environment(path):
    return dict(
        line.split("=", 1)
        for line in path.read_text().splitlines()
        if line and not line.startswith("#")
    )


def reset_test_database(settings, admin_password):
    live_database = settings.get("BASEX_DATABASE", "cloudmigration")
    database = live_database + "_tests"
    assert database.endswith("_tests") and database != live_database

    query = ET.Element("query", xmlns="http://basex.org/rest")
    ET.SubElement(query, "text").text = (
        "declare variable $db external; if(db:exists($db)) then "
        'db:delete($db, "") else db:create($db)'
    )
    ET.SubElement(query, "variable", name="db", value=database)
    request = urllib.request.Request(
        settings["BASEX_URL"],
        data=ET.tostring(query),
        headers={
            "Content-Type": "application/xml",
            "Authorization": "Basic "
            + base64.b64encode(("admin:" + admin_password).encode()).decode(),
        },
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        response.read()
    return database


def java_executable():
    if os.environ.get("JAVA_HOME"):
        return str(Path(os.environ["JAVA_HOME"]) / "bin/java")
    if sys.platform != "darwin":
        return "java"
    return (
        subprocess.check_output(
            ["/usr/libexec/java_home", "-v", "23"], text=True
        ).strip()
        + "/bin/java"
    )


def start_test_server(database):
    java = java_executable()
    os.chdir(ROOT / "backend")
    os.execv(
        java if "/" in java else shutil.which(java),
        [
            java,
            "-jar",
            "build/libs/cloudmigration-backend-0.0.1-SNAPSHOT.jar",
            "--server.port=8081",
            "--basex.database=" + database,
        ],
    )


settings = read_environment(ROOT / "backend/.env")
admin_password = read_environment(ROOT / "infra/.env")["BASEX_PASSWORD"]
database = reset_test_database(settings, admin_password)
start_test_server(database)
