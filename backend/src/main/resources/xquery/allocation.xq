declare variable $db external;
declare variable $report external;
declare variable $competition external;
declare variable $threshold external;
declare variable $offset external;
declare variable $limit external;
declare function local:allocate($rows as map(*)*, $remaining as xs:decimal) as map(*)* {
  if(empty($rows)) then () else
  let $row := head($rows)
  let $award := $row?qualified and xs:decimal($row?requestedAmount) le $remaining
  let $result := map:merge(($row,map{'outcome':if($award) then 'AWARD' else 'REJECT','remainingAfter':string(if($award) then $remaining - xs:decimal($row?requestedAmount) else $remaining)}),map{'duplicates':'use-last'})
  return ($result,local:allocate(tail($rows),if($award) then $remaining - xs:decimal($row?requestedAmount) else $remaining))
};
let $all := db:get($db)/*
let $c := $all[self::*:competition][@id=$competition]
let $apps := $all[self::*:application][*:competitionId=$competition]
let $issued := $all[self::*:decision][*:applicationId=$apps/@id][*:state='ISSUED']
let $evals := $all[self::*:evaluation][*:applicationId=$apps/@id][*:state='FINAL'][not(*:applicationId=$issued/*:applicationId)]
let $remaining := xs:decimal($c/*:budget)-sum($issued[*:outcome='AWARD']/*:amount ! xs:decimal(.))
let $rank := for $e in $evals let $a := $apps[@id=$e/*:applicationId]
 order by xs:integer($e/*:total) descending,xs:dateTime($a/*:submittedAt),string($a/@id)
 return map{'applicationId':string($a/@id),'evaluationId':string($e/@id),'title':string($a/*:title),'score':xs:integer($e/*:total),'qualified':$e/*:eligible='true' and xs:integer($e/*:total)>=xs:integer($c/*:threshold),'requestedAmount':string($a/*:requestedAmount)}
let $rows := local:allocate($rank,$remaining)
return if(empty($c)) then error(xs:QName('CM_NOT_FOUND')) else serialize(map{'total':count($rows),'rows':array{subsequence($rows,xs:integer($offset)+1,xs:integer($limit))}},map{'method':'json'})
