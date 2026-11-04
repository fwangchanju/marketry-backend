CREATE TABLE overseas_stock_classification (
    market VARCHAR(16) NOT NULL,
    stock_code VARCHAR(16) NOT NULL,
    sector_name VARCHAR(12),
    industry_name VARCHAR(12),
    PRIMARY KEY (market, stock_code)
);
