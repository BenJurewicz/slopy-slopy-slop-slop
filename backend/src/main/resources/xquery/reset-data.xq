declare variable $db external;

for $document in db:get($db)/*[
    self::*:application or self::*:evaluation or self::*:decision
]
return db:delete($db, db:path($document))
