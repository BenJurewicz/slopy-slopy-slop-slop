declare variable $db external;
declare variable $path external;
declare variable $expected external;
let $doc := if(db:exists($db,$path)) then db:get($db,$path)/* else error(xs:QName('CM_NOT_FOUND'))
let $id := string($doc/@id)
let $references := db:get($db)/*[*:competitionId=$id or *:applicationId=$id or *:evaluationId=$id]
return if($doc/self::*:competition or xs:integer($doc/@revision)!=xs:integer($expected) or exists($references) or $doc/*:state=('SUBMITTED','FINAL','ISSUED')) then error(xs:QName('CM_CONFLICT')) else db:delete($db,$path)
