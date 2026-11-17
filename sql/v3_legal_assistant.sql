CREATE TABLE IF NOT EXISTS edu_legal_assistant_record (
    record_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question VARCHAR(2000) NOT NULL,
    answer MEDIUMTEXT NULL,
    model_endpoint VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    error_message VARCHAR(160) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_assistant_owner (user_id, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,route_name,menu_type,perms,icon,create_by,create_time)
SELECT '法律助手记录',parent_id,order_num+1,'legal-assistant','ops/legalAssistant/index','LegalAssistantRecords','C',
       'ops:legalAssistant:list','message','admin',NOW()
FROM sys_menu
WHERE menu_id=2028 AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='ops:legalAssistant:list');
