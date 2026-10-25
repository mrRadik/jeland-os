"""Exercise exact compiled v2->v3 SQL against exported Room schema, without an APK."""
import argparse
import base64
import itertools
import json
from pathlib import Path
import re
import sqlite3
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--java-home", required=True)
parser.add_argument("--kotlin-stdlib", required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
classpath = str(root / "app/build/tmp/kotlin-classes/debug") + ";" + args.kotlin_stdlib
result = subprocess.run([str(Path(args.java_home) / "bin/java.exe"), "--class-path", classpath,
                         str(root / "tools/PrintSeatMigrationSql.java")], capture_output=True, text=True)
assert result.returncode == 0, result.stdout + result.stderr
match = re.search(r"SQL64:([A-Za-z0-9+/=]+)", result.stdout)
assert match, result.stdout + result.stderr
statements = base64.b64decode(match[1]).decode().splitlines()
schema = json.loads((root / "app/schemas/ru.jeland.os.data.automation.AutomationDatabase/2.json").read_text())['database']
db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
for entity in schema['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for index in entity.get('indices', []):
        db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
levels = ['KEEP', 'OFF', 'LEVEL_1', 'LEVEL_2', 'LEVEL_3']
cases = list(itertools.product(levels, repeat=6))
target = '(ruleId,componentType,position,seats_driverHeating,seats_driverVentilation,seats_passengerHeating,seats_passengerVentilation,seats_rearLeftHeating,seats_rearRightHeating,sunshade,steering)'
for n, values in enumerate(cases):
    key = str(n)
    db.execute("INSERT INTO automation_rules VALUES (?, ?, 0, 'BELOW', 5, 3, 41, 42)", (key, 'rule-' + key))
    db.execute(f"INSERT INTO automation_devices {target} VALUES (?, 'SUNSHADE', 0, NULL,NULL,NULL,NULL,NULL,NULL,'OPEN_HALF',NULL)", (key,))
    db.execute(f"INSERT INTO automation_devices {target} VALUES (?, 'SEATS', 1, ?,?,?,?,?,?, NULL,NULL)", (key, *values))
    db.execute(f"INSERT INTO automation_devices {target} VALUES (?, 'STEERING_WHEEL', 2, NULL,NULL,NULL,NULL,NULL,NULL,NULL,'HEAT_ON')", (key,))
db.execute("INSERT INTO automation_sessions VALUES ('trip', 40, 1)")
db.execute("INSERT INTO automation_runs VALUES ('trip','0','rule-0','SIMULATED','history','commands',45)")
db.execute("INSERT INTO automation_session_pointer VALUES (1,'trip',40)")
before = db.execute("SELECT * FROM automation_rules ORDER BY id").fetchall()
with db:
    for statement in statements:
        db.execute(statement)
devices = [('DRIVER_SEAT', [0, 1]), ('PASSENGER_SEAT', [2, 3]), ('REAR_LEFT_SEAT', [4]), ('REAR_RIGHT_SEAT', [5])]
for n, values in enumerate(cases):
    expected = [('SUNSHADE', 0, *([None] * 6), 'OPEN_HALF', None)]
    for offset, (device, indices) in enumerate(devices):
        if any(values[i] != 'KEEP' for i in indices):
            expected.append((device, 4 + offset, *(values[i] if i in indices else 'KEEP' for i in range(6)), None, None))
    expected.append(('STEERING_WHEEL', 8, *([None] * 6), None, 'HEAT_ON'))
    actual = db.execute("SELECT componentType,position,seats_driverHeating,seats_driverVentilation,seats_passengerHeating,seats_passengerVentilation,seats_rearLeftHeating,seats_rearRightHeating,sunshade,steering FROM automation_devices WHERE ruleId=? ORDER BY position", (str(n),)).fetchall()
    assert expected == actual, (values, expected, actual)
assert before == db.execute("SELECT * FROM automation_rules ORDER BY id").fetchall()
assert db.execute("SELECT * FROM automation_runs").fetchall() == [('trip','0','rule-0','SIMULATED','history','commands',45)]
assert db.execute("SELECT * FROM automation_session_pointer").fetchall() == [(1,'trip',40)]
assert not db.execute("PRAGMA foreign_key_check").fetchall()
db.close()
print(f"PASS: {len(cases)} legacy seat combinations; explicit OFF, sparse fields, order, metadata, journal, pointer and foreign keys preserved.")
