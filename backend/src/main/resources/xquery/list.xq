declare variable $db external;
declare variable $kind external;
<documents>{db:get($db, concat($kind,'s/'))/*}</documents>
