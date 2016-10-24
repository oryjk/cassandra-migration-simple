use bar;

update bar.role SET functions = ['RISK_ANALYSIS', 'PORTFOLIO_OPTIMIZATION', 'ATTRIBUTION', 'PORTFOLIO_MANAGEMENT', 'DASHBOARD','FACTOR_BACK_TESTING'] where key ='base';

INSERT INTO universe (id,i18n_key,name) VALUES ( 'csi300','csi300','沪深300');

INSERT INTO universe (id,i18n_key,name) VALUES ( 'csi500','csi500','中证500');

INSERT INTO universe (id,i18n_key,name) VALUES ( 'csi300|csi500','csi300|csi500','中证800');

INSERT INTO universe (id,i18n_key,name) VALUES ( 'all','all','A股全部');

