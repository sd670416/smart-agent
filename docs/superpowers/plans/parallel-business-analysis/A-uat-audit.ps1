param(
    [string]$TenantId = '000000',
    [string[]]$ProjectIds = @('2017044202274414593', '2018852340774342658', '2017472067503648770')
)

$ErrorActionPreference = 'Stop'
$aWorkspace = (Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
$aConfig = Get-Content (Join-Path $aWorkspace 'smart-boot/smart-app/src/main/resources/application-uat.yml') -Raw
$aConnection = [regex]::Match($aConfig, '(?m)^    url:\s*jdbc:mysql://([^:/]+):(\d+)/([^?\s]+)').Groups
if ($aConnection.Count -ne 4) { throw 'UAT JDBC configuration not recognized.' }
$aUsername = [regex]::Match($aConfig, '(?m)^    username:\s*(.+)\r?$').Groups[1].Value.Trim()
$aPassword = [regex]::Match($aConfig, '(?m)^    password:\s*(.+)\r?$').Groups[1].Value.Trim()
if ($ProjectIds.Count -eq 0 -or $ProjectIds.Count -gt 100) { throw 'Choose 1 to 100 audit projects.' }
$script:aIdsSql = '(' + (($ProjectIds | ForEach-Object { "'" + $_.Replace("'", "''") + "'" }) -join ',') + ')'
$script:aTenantSql = "'" + $TenantId.Replace("'", "''") + "'"
$script:aFragments = @{}
foreach ($aRelative in @(
    'smart-boot/smart-business/smart-budget/src/main/resources/mapper/BudgetCostAiDao.xml',
    'smart-boot/smart-business/smart-project/src/main/resources/mapper/ProjectAnalysisMetricDao.xml'
)) {
    $aDocument = [xml](Get-Content (Join-Path $aWorkspace $aRelative) -Raw)
    $aNamespace = $aDocument.mapper.namespace
    foreach ($aNode in $aDocument.mapper.ChildNodes) {
        if ($aNode.NodeType -eq [Xml.XmlNodeType]::Element) {
            $script:aFragments["$aNamespace.$($aNode.GetAttribute('id'))"] = $aNode
        }
    }
}

# Expand the limited MyBatis XML nodes used by these read-only CURRENT queries.
function Expand-AQuery([Xml.XmlNode]$Node, [string]$Namespace) {
    if ($Node.NodeType -ne [Xml.XmlNodeType]::Element) { return $Node.Value }
    switch ($Node.Name) {
        'include' {
            $aRef = $Node.GetAttribute('refid')
            if (!$aRef.Contains('.')) { $aRef = "$Namespace.$aRef" }
            if (!$script:aFragments.ContainsKey($aRef)) { throw "Missing SQL fragment $aRef" }
            return Expand-AQuery $script:aFragments[$aRef] $aRef.Substring(0, $aRef.LastIndexOf('.'))
        }
        'foreach' { return $script:aIdsSql }
        'if' {
            if ($Node.GetAttribute('test') -notin @('request.projectName != null', 'request.startDate != null')) {
                throw 'Audit expansion encountered an unsupported conditional.'
            }
            return ''
        }
        'choose' {
            $aBranch = @($Node.ChildNodes | Where-Object {
                $_.NodeType -eq [Xml.XmlNodeType]::Element -and $_.Name -eq 'when' -and
                $_.GetAttribute('test') -eq "key == '$script:aMetricKey'"
            })
            if ($aBranch.Count -ne 1) { throw "Unknown source metric $script:aMetricKey" }
            return Expand-AQuery $aBranch[0] $Namespace
        }
    }
    $aParts = foreach ($aChild in $Node.ChildNodes) { Expand-AQuery $aChild $Namespace }
    return ($aParts -join ' ')
}

function Get-AQuery([string]$Statement) {
    $aNamespace = 'com.smart.project.analysis.core.ProjectAnalysisMetricDao'
    $aSql = Expand-AQuery $script:aFragments["$aNamespace.$Statement"] $aNamespace
    $aSql = $aSql.Replace('#{scope.tenantId}', $script:aTenantSql).Replace('#{request.tenantId}', $script:aTenantSql)
    if ($aSql.Contains('#{') -or $aSql.Contains('${')) { throw 'Unexpanded query parameter.' }
    return $aSql
}

$aStatements = [Collections.Generic.List[string]]::new()
$aStatements.Add('START TRANSACTION READ ONLY')
$aStatements.Add('SELECT DATABASE() AS audit_database, VERSION() AS database_version')
$aStatements.Add((Get-AQuery 'projects'))
foreach ($script:aMetricKey in @('finalContractAmount', 'paymentReceivedAmount', 'otherIncomeAmount',
    'paymentAmount', 'otherPaymentAmount', 'reimburseAmount', 'refundAmount', 'invoiceApplicationAmount', 'targetCostAmount')) {
    $aSourceSql = Get-AQuery 'source'
    $aStatements.Add("SELECT '$script:aMetricKey' AS metric,projectId,COUNT(recordId) AS recordCount,SUM(amount) AS amount,SUM(invalid) AS invalid FROM ($aSourceSql) source_rows GROUP BY projectId")
}
$aBudgetSql = Get-AQuery 'budget'
$aStatements.Add("SELECT 'effectiveTargetCost' AS metric,b.* FROM ($aBudgetSql) b")
$aStatements.Add('ROLLBACK')
$aPreviousPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
try {
    $env:MYSQL_PWD = $aPassword
    & 'F:/mysql/MySQL Server 8.3/bin/mysql.exe' --protocol=TCP --connect-timeout=10 --default-character-set=utf8mb4 `
        -h $aConnection[1].Value -P $aConnection[2].Value -u $aUsername -D $aConnection[3].Value -B -r `
        -e (($aStatements -join ";`n") + ';')
    if ($LASTEXITCODE -ne 0) { throw "Read-only audit failed: mysql exit $LASTEXITCODE" }
} finally {
    if ($null -eq $aPreviousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $aPreviousPassword }
}
