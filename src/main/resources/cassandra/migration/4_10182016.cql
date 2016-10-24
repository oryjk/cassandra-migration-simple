use bar;

drop table if exists benchmarkreturns;

CREATE TABLE if not exists benchmark_returns (
    benchmarkkey text,
    tradingdate date,
    rateofreturn double,
    PRIMARY KEY (benchmarkkey, tradingdate)
) WITH CLUSTERING ORDER BY (tradingdate ASC);


drop table if exists benchmarksectorreturns;

CREATE TABLE if not exists benchmark_sector_returns (
    benchmarkkey text,
    tradingdate date,
    sectorid text,
    rateofreturn double,
    weight double,
    PRIMARY KEY (benchmarkkey, tradingdate, sectorid)
) WITH CLUSTERING ORDER BY (tradingdate ASC, sectorid ASC);

