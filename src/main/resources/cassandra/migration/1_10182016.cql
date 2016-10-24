//db.1.0.cql

CREATE KEYSPACE IF NOT EXISTS bar WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 } AND DURABLE_WRITES = true;

use bar;

CREATE TABLE IF NOT EXISTS bar.UserRegistry (
    id UUID PRIMARY KEY,
    username text,
    password text,
    role text
);

CREATE TABLE IF NOT EXISTS bar.UsernameId (
    username text PRIMARY KEY,
    id UUID,
);

CREATE TABLE IF NOT EXISTS bar.RiskModel (
    id text PRIMARY KEY,
    name text,
    i18n_key text,
    factors list<text>
);

CREATE TABLE IF NOT EXISTS bar.Portfolio (
    userId uuid,
    portfolioId timeuuid,
    categoryMaxValue int,
    name text,
    indexId text,
    riskModelId text,
    riskMeasurement text,
    attributionMeasurement text,
    attributionMethodId text,
    analysisMethod text,
    scenarioId uuid,
    constraintId uuid,
    categoryName text,
    historyStart date,
    historyEnd date,
    benchmarkId text,
    PRIMARY KEY (userId, portfolioId)
)
WITH CLUSTERING ORDER BY (portfolioId desc);

CREATE TABLE IF NOT EXISTS bar.Attributionsource (
    userId uuid,
    portfolioId timeuuid,
    indexId text,
    riskModelId text,
    riskMeasurement text,
    attributionMeasurement text,
    attributionMethodId text,
    analysisMethod text,
    scenarioId uuid,
    constraintId uuid,
    categoryName text,
    PRIMARY KEY (userId, portfolioId)
)
WITH CLUSTERING ORDER BY (portfolioId desc);

CREATE TABLE IF NOT EXISTS bar.PortfolioComponent (
    portfolioId timeuuid,
    componentId timeuuid,
    code text,
    weight decimal,
    categoryValue decimal,
    PRIMARY KEY (portfolioId, componentId)
)
WITH CLUSTERING ORDER BY (componentId desc);

//<initial constraint table>
CREATE TYPE IF NOT EXISTS bar.FactorConstraint (
    key text,
    maxExposure double,
    minExposure double
);
CREATE TYPE IF NOT EXISTS  bar.GoalConstraint (
    type text,
    maxRisk double,
    minReturn double,
    riskMeasurement text
);
create TYPE IF NOT EXISTS  bar.IndustryConstraint(
	id text,
 	maxWeight double,
    minWeight double
);
create TYPE IF NOT EXISTS  bar.SectorConstraint(
	id text,
	maxWeight double,
    minWeight double
);
create TYPE IF NOT EXISTS  bar.StockConstraint(
	code text,
    maxWeight double,
    minWeight double
);
create TYPE IF NOT EXISTS  bar.StockConstraint(
	code text,
    maxWeight double,
    minWeight double
);
create TYPE IF NOT EXISTS  bar.CashConstraint(
    maxWeight double,
    minWeight double
);

//</initial constraint table>
CREATE TABLE IF NOT EXISTS bar.constraint (
    userid uuid,
    id timeuuid,
    cashconstraint frozen<cashconstraint>,
    factors list<frozen<factorconstraint>>,
    globalindustry frozen<industryconstraint>,
    globalsector frozen<sectorconstraint>,
    globalstock frozen<stockconstraint>,
    goal frozen<goalconstraint>,
    maxturnoverrate double,
    name text,
    sectorreportlist list<frozen<industryconstraint>>,
    sectors list<frozen<sectorconstraint>>,
    stocks list<frozen<stockconstraint>>,
    updateAt timestamp,
    PRIMARY KEY (userid, id)
) WITH CLUSTERING ORDER BY (id ASC);
CREATE INDEX IF NOT EXISTS constraint_name_key ON bar.constraint (name);
//CREATE INDEX IF NOT EXISTS constraint_id_key ON bar.constraint (id);

//<role>
CREATE TABLE IF NOT EXISTS bar.Role (
    key text PRIMARY KEY,
    functions list<text>
);

create table if not exists bar.portfoliocomponenthistory(
    portfolioid timeuuid,
    recordTime date,
    code text,
    categoryvalue decimal,
    tradingstatus text,
    weight decimal,
    sectorId text,
    industryId text,
    amount double,
    relativeCategoryValue double,
    PRIMARY KEY (portfolioid,recordTime,code)
)
WITH CLUSTERING ORDER BY (recordTime desc);

create table if not exists bar.tradingDay(
    key text,
    tradingday timestamp,
    PRIMARY KEY (key, tradingday)
)
WITH CLUSTERING ORDER BY (tradingday asc);

create table if not exists bar.BenchmarkStockWeight(
    indexId text,
    recordTime date,
    code text,
    weight double,
    PRIMARY KEY (indexId,recordTime,code)
)
WITH CLUSTERING ORDER BY (recordTime desc);
CREATE INDEX IF NOT EXISTS BenchmarkStockWeight_recordTime ON bar.BenchmarkStockWeight (recordTime);

create table if not exists bar.StockMarketInfo(
    code text,
    recordTime timestamp,
    closePrice double,
    circulationMarketValue double,
    totalMarketValue double,
    returnValue double,
    industryId text,
    sectorId text,
    PRIMARY KEY (code,recordTime)
)
WITH CLUSTERING ORDER BY (recordTime desc);

create table if not exists bar.BenchmarkReturns (
    benchmarkKey text,
    tradingDay timestamp,
    rateOfReturn double,
    PRIMARY KEY (benchmarkKey, tradingDay)
)
WITH CLUSTERING ORDER BY (tradingDay asc);


create table if not exists bar.BenchmarkSectorReturns (
    benchmarkKey text,
    tradingDay timestamp,
    sectorId text,
    weight double,
    rateOfReturn double,
    PRIMARY KEY (benchmarkKey, tradingDay, sectorId)
)
WITH CLUSTERING ORDER BY (tradingday asc);

create table if not exists bar.UserAttributionConfig (
    userId uuid PRIMARY KEY,
    id uuid,
    attributionMethod text,
    attributionMeasurement text,
    benchmarkId text
);


//db.pokemon.cql

CREATE TABLE IF NOT EXISTS bar.UserAnalysisConfig (
    userId uuid PRIMARY KEY,
    id uuid,
    benchmarkId text,
    riskMeasurement text,
    riskModelid text,
    scenarioId text
);

CREATE TABLE IF NOT EXISTS bar.UserOptimizationconfig (
    userId uuid PRIMARY KEY,
    id uuid,
    benchmarkId text,
    constraintId uuid,
    riskMeasurement text,
    riskModelId text
);

CREATE TABLE IF NOT EXISTS bar.StockWindCode (
    tradingday timestamp,
    stockCode text,
    windCode text,
    PRIMARY KEY (stockCode, tradingday)
);

CREATE TABLE IF NOT EXISTS bar.StockTransferMarketInfo (
    code text,
    date timestamp,
    dateNum int,
    closePrice double,
    turnover double,
    circulationMarketValue double,
    PBRatio double,
    Leverage double,
    WDCode text,
    Tradable int,
    PRIMARY KEY (code, date)
);

CREATE TABLE IF NOT EXISTS bar.FinancialIndex (
    id text PRIMARY KEY,
    name text,
    englishName text
);

CREATE TABLE IF NOT EXISTS bar.Sector (
    id text PRIMARY KEY,
    name text,
    englishname text
);
CREATE INDEX IF NOT EXISTS sector_name_key ON bar.Sector (name);

CREATE TABLE IF NOT EXISTS bar.Industry (
    id text,
    sectorId text,
    name text,
    englishname text,
    PRIMARY KEY (sectorId, id)
);
CREATE INDEX IF NOT EXISTS Industry_id_key ON bar.Industry (id);
CREATE INDEX IF NOT EXISTS Industry_name_key ON bar.Industry (name);


//db.fyc.cql

create table if not exists bar.BaseModelFactorExposure (
    key text,
    tradingDay date,
    stockCode text,
    stockReturn double,
    residue double,
    residueSTD double,
    RFR double,
    REV double,
    MOM double,
    CAP double,
    LIQ double,
    BAL double,
    PBR double,
    ENG double,
    MAT double,
    IND double,
    COD double,
    COS double,
    HEA double,
    FIN double,
    INF double,
    UTI double,
    PRIMARY KEY (key, tradingDay, stockCode)
)WITH CLUSTERING ORDER BY (tradingday asc);

create table if not exists bar.BaseModelFactorReturn (
    key text,
    tradingDay date,
    RFR double,
    REV double,
    MOM double,
    CAP double,
    LIQ double,
    BAL double,
    PBR double,
    ENG double,
    MAT double,
    IND double,
    COD double,
    COS double,
    HEA double,
    FIN double,
    INF double,
    UTI double,
    PRIMARY KEY (key, tradingDay)
)
WITH CLUSTERING ORDER BY (tradingday asc);

create table if not exists bar.BaseModelRegressedFactorReturn (
    key text,
    tradingDay timestamp,
    RFR double,
    REV double,
    MOM double,
    CAP double,
    LIQ double,
    BAL double,
    PBR double,
    ENG double,
    MAT double,
    IND double,
    COD double,
    COS double,
    HEA double,
    FIN double,
    INF double,
    UTI double,
    PRIMARY KEY (key, tradingDay)
)
WITH CLUSTERING ORDER BY (tradingday asc);

CREATE TYPE IF NOT EXISTS bar.TypeName (
    name text,
    i18n_key text
);
CREATE TYPE IF NOT EXISTS bar.NameName (
    name text,
    i18n_key text
);
CREATE TABLE IF NOT EXISTS bar.Factor (
    id UUID PRIMARY KEY,
    code text,
    name frozen<NameName>,
    categoryId text,
    type frozen<TypeName>,
    source text,
    formula text,
    riskModelIds list<text>,
    tag text,
    isFavorite boolean
);

CREATE TABLE IF NOT EXISTS bar.FactorUser (
	userId UUID,
	factorId UUID,
	code text,
	name frozen<NameName>,
    categoryId text,
    type frozen<TypeName>,
    source text,
    formula text,
    riskModelIds list<text>,
    tag text,
    isFavorite boolean,
    PRIMARY KEY (userId,factorId)
)WITH CLUSTERING ORDER BY (factorId desc);

CREATE TABLE IF NOT EXISTS bar.FactorSystem (
    id UUID PRIMARY KEY,
    code text,
    name frozen<NameName>,
    categoryId text,
    type frozen<TypeName>,
    source text,
    formula text,
    riskModelIds list<text>,
    tag text,
    isFavorite boolean
);

CREATE TABLE IF NOT EXISTS bar.FactorCategory(
    name text primary key,
    nameKey text,
    description text,
    descriptionKey text
);

CREATE TABLE IF NOT EXISTS bar.StockBasicInformation (
    code text PRIMARY KEY,
    name text,
    pinyin text,
    pinyinInitials text,
    status text,
    sectorId text,
    industryId text,
    englishName text
);

DELETE FROM bar.financialindex WHERE id ='csi500';
update bar.role SET functions = ['RISK_ANALYSIS', 'PORTFOLIO_OPTIMIZATION', 'ATTRIBUTION', 'PORTFOLIO_MANAGEMENT', 'DASHBOARD'] where key ='base';

create table if not exists bar.stockyield (
    tradingdate date,
    code text,
    tradeStatus int,
    dayyield double,
    nextdayyield double,
    nextweekyield double,
    nextmonthyield double,
    primary KEY (tradingdate, code)
);

create table if not exists bar.stockyields (
    code text,
    tradingdate date,
    dayyield double,
    nextdayyield double,
    nextweekyield double,
    nextmonthyield double,
    primary KEY (code, tradingdate )
) WITH CLUSTERING ORDER BY (tradingdate asc);

create table if not exists bar.stockyields_by_date (
    tradingdate date,
    code text,
    dayyield double,
    nextdayyield double,
    nextweekyield double,
    nextmonthyield double,
    primary KEY (tradingdate, code)
);

CREATE TABLE if not exists bar.factor_exposures_by_date (
    factorid uuid,
    tradingdate date,
    stockcode text,
    exposure double,
    PRIMARY KEY (factorid, tradingdate, stockcode)
) WITH CLUSTERING ORDER BY (tradingdate ASC, stockcode ASC);

//create tradingDate table, tradingDay is deprecated
CREATE TABLE if not exists bar.tradingdate (
    key text,
    tradingdate date,
    PRIMARY KEY (key, tradingdate)
) WITH CLUSTERING ORDER BY (tradingdate ASC);

create table if not exists bar.portfoliohistory(
    portfolioid timeuuid,
    recordTime date,
    cash decimal,
    netAssetValue double,
    PRIMARY KEY (portfolioid,recordTime)
)
WITH CLUSTERING ORDER BY (recordTime desc);

Create table if not exists bar.UserFactorBackTestingConfig(
    userId uuid,
    universeId text,
    groupCount int,
    dateFrom date,
    dateTo date,
    frequency text,
    factors list<uuid>,primary key(userId)
    );

create table if not exists bar.Universe (
    id text,
    name text,
    i18n_key text,
    PRIMARY KEY (id)
);

create table if not exists bar.stock_universes(
    id text,
    tradingdate date,
    stockcode text,
    weight double,
    PRIMARY KEY (id,tradingdate,stockcode)
)
WITH CLUSTERING ORDER BY (tradingdate desc);


