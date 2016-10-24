use bar;

drop table if exists stockyields;

drop table if exists stockyields_by_date;

drop table if exists stockyield;

CREATE TABLE if not exists stockyield (
    tradingdate date,
    code text,
    dayyield double,
    nextdayyield double,
    nextmonthyield double,
    nextweekyield double,
    tradestatus int,
    PRIMARY KEY (tradingdate, code)
) WITH CLUSTERING ORDER BY (code ASC);
