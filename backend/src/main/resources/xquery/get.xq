declare variable $db external;
declare variable $path external;

if (db:exists($db, $path)) then db:get($db, $path)
else error(xs:QName('CM_NOT_FOUND'))
