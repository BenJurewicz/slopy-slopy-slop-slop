#!/usr/bin/env python3
"""Enrich fictional v1 demo records in place, atomically, WITHOUT making a backup."""

import argparse
import base64
import copy
import os
import shutil
import subprocess
import urllib.request
from collections import Counter
from datetime import datetime
from decimal import Decimal
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


READ_DOCUMENTS = (
    "declare variable $db external; "
    "<documents>{for $d in db:get($db)/* return "
    '<stored path="{db:path($d)}">{$d}</stored>}</documents>'
)

APPLY_MIGRATION = """declare variable $db external; declare variable $count external;
let $entries := /*:migration/*:document
return if(count(db:get($db)/*) ne xs:integer($count) or
  (some $e in $entries satisfies not(count(db:get($db,string($e/@path))/*)=1 and
   string(db:get($db,string($e/@path))/*/@revision)=string($e/@revision) and
   namespace-uri(db:get($db,string($e/@path))/*)=string($e/@namespace))))
then error(xs:QName('CM_CONFLICT'),'Database changed; rerun enrichment')
else for $e in $entries return db:put($db,$e/*,string($e/@path))"""


def read_environment(path):
    return dict(
        line.split("=", 1)
        for line in path.read_text().splitlines()
        if line and not line.startswith("#")
    )


def local_name(element):
    return element.tag.split("}")[-1]


def find_field(element, name):
    return next((child for child in element if local_name(child) == name), None)


def field_text(element, name):
    found = find_field(element, name)
    return "" if found is None else found.text or ""


def add_missing_field(element, name, value, namespace=None):
    if find_field(element, name) is None:
        namespace = namespace or element.tag[1:].split("}")[0]
        ET.SubElement(element, "{" + namespace + "}" + name).text = str(value)


def assert_preserved(before, after):
    """All original element values, order and attributes must survive enrichment."""
    assert before.tag.replace(":v1}", ":v2}") == after.tag
    assert (
        before.attrib == after.attrib
        and (before.text or "").strip() == (after.text or "").strip()
    )
    assert len(after) >= len(before)
    for old, new in zip(before, after):
        assert_preserved(old, new)


def reconstruct_budget_snapshots(records):
    budget_flags = {}
    for competition in (
        record
        for record in records.values()
        if local_name(record) == "competition"
    ):
        remaining = Decimal(field_text(competition, "budget"))
        evaluations = [
            record
            for record in records.values()
            if local_name(record) == "evaluation"
            and field_text(record, "state") == "FINAL"
            and field_text(
                records[field_text(record, "applicationId")], "competitionId"
            )
            == competition.get("id")
        ]
        evaluations.sort(
            key=lambda evaluation: (
                -int(field_text(evaluation, "total")),
                datetime.fromisoformat(
                    field_text(
                        records[field_text(evaluation, "applicationId")],
                        "submittedAt",
                    ).replace("Z", "+00:00")
                ),
                field_text(evaluation, "applicationId"),
            )
        )
        for evaluation in evaluations:
            requested = Decimal(
                field_text(
                    records[field_text(evaluation, "applicationId")],
                    "requestedAmount",
                )
            )
            allows = requested <= remaining
            budget_flags[evaluation.get("id")] = allows
            if (
                allows
                and field_text(evaluation, "eligible") == "true"
                and int(field_text(evaluation, "total"))
                >= int(field_text(competition, "threshold"))
            ):
                remaining -= requested
    return budget_flags


def enrich_application(document, identity, document_number):
    company = find_field(document, "company")
    index = int(identity[:8], 16) % 999
    add_missing_field(
        company,
        "address",
        f"ul. Przykładowa {index + 1}, 80-001 Gdańsk",
        "urn:cloudmigration:common:v2",
    )
    add_missing_field(
        company,
        "regon",
        f"{100000000 + index:09d}",
        "urn:cloudmigration:common:v2",
    )
    add_missing_field(
        company,
        "representative",
        field_text(company, "contact")
        + " - osoba upoważniona do reprezentacji",
        "urn:cloudmigration:common:v2",
    )
    tasks = [task for task in document if local_name(task) == "tasks"]
    start, end = (
        min(field_text(task, "start") for task in tasks),
        max(field_text(task, "end") for task in tasks),
    )
    cost = sum(
        (
            Decimal(field_text(line, "total"))
            for line in document
            if local_name(line) == "budget"
        ),
        Decimal("0.00"),
    )
    own = cost - Decimal(field_text(document, "requestedAmount"))
    assert own >= 0, "Requested grant exceeds existing project budget"
    add_missing_field(document, "number", document_number("WN"))
    add_missing_field(document, "projectNumber", document_number("PR"))
    add_missing_field(document, "implementationStart", start)
    add_missing_field(document, "implementationEnd", end)
    add_missing_field(document, "projectCost", f"{cost:.2f}")
    add_missing_field(document, "ownContribution", f"{own:.2f}")


def enrich_record(old, budget_flags):
    document = copy.deepcopy(old)
    for node in document.iter():
        node.tag = node.tag.replace(":v1}", ":v2}")
    kind, identity = local_name(document), document.get("id")
    year = field_text(document, "date")[:4] or "2026"

    def document_number(prefix):
        return f"{prefix}/{year}/{identity[:8].upper()}"

    if kind == "application":
        enrich_application(document, identity, document_number)
    elif kind == "evaluation":
        add_missing_field(document, "number", document_number("OC"))
    elif kind == "decision":
        add_missing_field(document, "number", document_number("DEC"))
        allows = field_text(document, "outcome") == "AWARD" or budget_flags.get(
            field_text(document, "evaluationId"), False
        )
        add_missing_field(document, "budgetAllows", str(allows).lower())
    assert_preserved(old, document)
    return document


def build_migration_payload(original):
    payload = ET.Element("migration")
    records = {entry[0].get("id"): entry[0] for entry in original}
    budget_flags = reconstruct_budget_snapshots(records)
    for stored in original:
        old = stored[0]
        if ":v1}" not in old.tag:
            continue
        document = enrich_record(old, budget_flags)
        entry = ET.SubElement(
            payload,
            "document",
            path=stored.get("path"),
            revision=old.get("revision"),
            namespace=old.tag[1:].split("}")[0],
        )
        entry.append(document)
    return payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply after XML validation; default is a read-only check.",
    )
    args = parser.parse_args()
    settings = read_environment(ROOT / "backend/.env")
    password = read_environment(ROOT / "infra/.env")["BASEX_PASSWORD"]
    database = settings["BASEX_DATABASE"]
    authorization = (
        "Basic " + base64.b64encode(("admin:" + password).encode()).decode()
    )

    def query(source, context=None, variables=()):
        request = ET.Element("query", xmlns="http://basex.org/rest")
        ET.SubElement(request, "text").text = source
        ET.SubElement(request, "variable", name="db", value=database)
        for name, value in variables:
            ET.SubElement(request, "variable", name=name, value=str(value))
        if context is not None:
            ET.SubElement(request, "context").append(context)
        call = urllib.request.Request(
            settings["BASEX_URL"],
            data=ET.tostring(request),
            headers={
                "Content-Type": "application/xml",
                "Authorization": authorization,
            },
        )
        with urllib.request.urlopen(call, timeout=45) as response:
            return response.read()

    original = ET.fromstring(query(READ_DOCUMENTS))
    payload = build_migration_payload(original)
    if not len(payload):
        print(
            "No v1 documents to enrich. Database unchanged; no backup created."
        )
        return
    java = (
        str(Path(os.environ["JAVA_HOME"]) / "bin/java")
        if "JAVA_HOME" in os.environ
        else shutil.which("java")
    )
    subprocess.run(
        [
            java,
            str(Path(__file__).with_name("ValidateXml.java")),
            str(ROOT / "backend/src/main/resources/schemas"),
        ],
        input=ET.tostring(payload),
        check=True,
    )
    print(
        f"Prepared {len(payload)} records: {dict(Counter(local_name(entry[0]) for entry in payload))}. Existing fields and revisions preserved."
    )
    if not args.apply:
        print("Read-only check; use --apply to enrich without a backup.")
        return
    query(
        APPLY_MIGRATION,
        payload,
        [("count", len(original))],
    )
    current = ET.fromstring(query(READ_DOCUMENTS))
    assert len(current) == len(original)
    updated = {entry.get("path"): entry[0] for entry in current}
    for entry in original:
        assert_preserved(entry[0], updated[entry.get("path")])
    print(
        f"Enriched {len(payload)} records atomically. IDs, links, amounts, scores, states and revisions unchanged. No backup created."
    )


if __name__ == "__main__":
    main()
