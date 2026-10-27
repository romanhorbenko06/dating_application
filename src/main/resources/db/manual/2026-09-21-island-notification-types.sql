-- Нові типи сповіщень для «Острова на двох».
-- ddl-auto=update не оновлює наявний CHECK на notifications.type, тож запускати вручну
-- один раз на кожній базі, створеній до 21.09.2026.

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (
    type IN ('NEW_LIKE', 'NEW_MATCH', 'NEW_MESSAGE', 'ISLAND_INVITE', 'ISLAND_UPDATE', 'ISLAND_COMPLETED')
);
