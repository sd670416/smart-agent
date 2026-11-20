$ErrorActionPreference = 'Stop'
$gRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
$gConfig = Get-Content (Join-Path $gRoot 'smart-boot/smart-app/src/main/resources/application-uat.yml') -Raw
$gConnection = [regex]::Match($gConfig, '(?m)^    url:\s*jdbc:mysql://([^:/]+):(\d+)/([^?\s]+)').Groups
if ($gConnection.Count -ne 4) { throw 'UAT JDBC configuration not recognized.' }
$gUser = [regex]::Match($gConfig, '(?m)^    username:\s*(.+)\r?$').Groups[1].Value.Trim()
$gSecret = [regex]::Match($gConfig, '(?m)^    password:\s*(.+)\r?$').Groups[1].Value.Trim()
$gXml = [xml](Get-Content (Join-Path $gRoot 'smart-boot/smart-business/smart-project/src/main/resources/mapper/ProjectSiteAnalysisDao.xml') -Raw)
$gIdsSql = "(SELECT id FROM bus_project WHERE tenant_id='000000' AND is_deleted='0' AND approval_status='2' AND project_status NOT IN ('1','3','10'))"
function Expand-GNode([Xml.XmlNode] $gNode, [string] $gSource) {
    if ($gNode.NodeType -ne [Xml.XmlNodeType]::Element) { return $gNode.Value }
    switch ($gNode.Name) {
        'include' { return Expand-GNode ($gXml.mapper.sql | Where-Object { $_.id -eq $gNode.refid }) $gSource }
        'foreach' { return $gIdsSql }
        'choose' {
            foreach ($gWhen in $gNode.SelectNodes('when')) {
                if ($gWhen.test -eq "source == '$gSource'") { return Expand-GNode $gWhen $gSource }
            }
            return Expand-GNode $gNode.SelectSingleNode('otherwise') $gSource
        }
        'if' { if ($gNode.test -eq "source == '$gSource'") { return Expand-GNodeChildren $gNode $gSource }; return '' }
        default { return Expand-GNodeChildren $gNode $gSource }
    }
}
function Expand-GNodeChildren([Xml.XmlNode] $gNode, [string] $gSource) {
    return (($gNode.ChildNodes | ForEach-Object { Expand-GNode $_ $gSource }) -join ' ')
}
$gStatements = [Collections.Generic.List[string]]::new()
$gStatements.Add('START TRANSACTION READ ONLY;')
$gRulesSelect = $gXml.mapper.select | Where-Object { $_.id -eq 'ruleConfigurations' }
$gRulesSql = (Expand-GNode $gRulesSelect '').Replace('#{tenantId}', "'000000'")
$gStatements.Add("SELECT 'site rule configurations' AS source,COUNT(*) AS matchedRows FROM ($gRulesSql) audit_rules;")
foreach ($gKind in @('contract','schedule','feedback','log','acceptance','quality','safety')) {
    $gSelect = $gXml.mapper.select | Where-Object { $_.id -eq 'records' }
    $gSql = (Expand-GNode $gSelect $gKind).Replace('#{scope.tenantId}', "'000000'")
    $gStatements.Add("SELECT '$gKind' AS source, COUNT(*) AS matchedRows FROM ($gSql) audit_rows;")
}
foreach ($gKind in @('quality','safety')) {
    $gSelect = $gXml.mapper.select | Where-Object { $_.id -eq 'issues' }
    $gSql = (Expand-GNode $gSelect $gKind).Replace('#{scope.tenantId}', "'000000'")
    $gStatements.Add("SELECT '$gKind issues' AS source, COUNT(*) AS matchedRows FROM ($gSql) audit_rows;")
}
$gStatements.Add("SELECT d.dict_name,d.dict_value FROM sys_dict d JOIN sys_dict parent ON parent.id=d.parent_id AND parent.tenant_id=d.tenant_id WHERE parent.dict_code='bus_project_status' AND parent.tenant_id='000000' AND d.is_deleted='0';")
$gStatements.Add('ROLLBACK;')
$gPreviousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $gSecret
    & 'F:/mysql/MySQL Server 8.3/bin/mysql.exe' --protocol=TCP --connect-timeout=10 --default-character-set=utf8mb4 -h $gConnection[1].Value -P $gConnection[2].Value -u $gUser -D $gConnection[3].Value -B -r -e ($gStatements -join "`n")
    if ($LASTEXITCODE -ne 0) { throw 'Read-only G mapper audit failed.' }
} finally {
    if ($null -eq $gPreviousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $gPreviousPassword }
}
