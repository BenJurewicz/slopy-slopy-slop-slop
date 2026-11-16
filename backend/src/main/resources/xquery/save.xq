declare variable $db external;
declare variable $kind external;
declare variable $path external;
declare variable $expected external;
let $new := /*
let $old := if(db:exists($db,$path)) then db:get($db,$path)/* else ()
let $all := db:get($db)/*
let $competition := $all[self::*:competition][@id=$new/*:competitionId]
let $application := $all[self::*:application][@id=$new/*:applicationId]
let $evaluation := $all[self::*:evaluation][@id=$new/*:evaluationId]
let $config := $all[self::*:competition][@id=$application/*:competitionId]
let $otherEvaluations := $all[self::*:evaluation][*:applicationId=$new/*:applicationId][@id!=$new/@id]
let $otherDecisions := $all[self::*:decision][*:applicationId=$new/*:applicationId][@id!=$new/@id]
return
if((empty($old) and xs:integer($expected)!=0) or (exists($old) and xs:integer($old/@revision)!=xs:integer($expected)) or xs:integer($new/@revision)!=xs:integer($expected)+1) then error(xs:QName('CM_CONFLICT'))
else if(exists($old) and ($old/*:state=('SUBMITTED','FINAL','ISSUED'))) then error(xs:QName('CM_CONFLICT'))
else if(empty($old) and $new/*:state!= 'DRAFT') then error(xs:QName('CM_RULE'))
else if($kind='competition' and exists($all[self::*:competition][@id!=$new/@id])) then error(xs:QName('CM_CONFLICT'))
else if($kind='competition' and exists($old) and not(deep-equal($old/*:name,$new/*:name))) then error(xs:QName('CM_RULE'))
else if($kind='competition' and exists($all[self::*:application][*:competitionId=$new/@id]) and not(deep-equal($old/*[not(self::*:stage)],$new/*[not(self::*:stage)]))) then error(xs:QName('CM_CONFLICT'))
else if($kind='application' and (count($all[self::*:competition])!=1 or empty($competition) or $competition/*:stage!='OPEN' or ($old and $new/*:competitionId!=$old/*:competitionId))) then error(xs:QName('CM_RULE'))
else if($kind='evaluation' and (empty($application) or $application/*:state!='SUBMITTED' or exists($otherEvaluations) or ($old and $old/*:applicationId!=$new/*:applicationId))) then error(xs:QName('CM_RULE'))
else if($kind='decision' and (empty($evaluation) or $evaluation/*:state!='FINAL' or $evaluation/*:applicationId!=$new/*:applicationId or exists($otherDecisions) or ($old and ($old/*:applicationId!=$new/*:applicationId or $old/*:evaluationId!=$new/*:evaluationId)))) then error(xs:QName('CM_RULE'))
else if($kind='decision' and $new/*:state='ISSUED') then error(xs:QName('CM_RULE'))
else db:put($db,$new,$path)
