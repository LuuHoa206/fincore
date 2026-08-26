INSERT INTO categories (name, category_type, icon, color) VALUES
    ('Luong', 'INCOME', 'banknote', '#0F8F72'),
    ('Thuong', 'INCOME', 'sparkles', '#16A34A'),
    ('Ban hang', 'INCOME', 'shopping-bag', '#2563EB'),
    ('Dau tu', 'INCOME', 'chart-no-axes-combined', '#7C3AED'),
    ('An uong', 'EXPENSE', 'utensils', '#EA580C'),
    ('Di chuyen', 'EXPENSE', 'car-front', '#2563EB'),
    ('Nha cua', 'EXPENSE', 'house', '#7C3AED'),
    ('Hoa don', 'EXPENSE', 'receipt-text', '#DC2626'),
    ('Suc khoe', 'EXPENSE', 'heart-pulse', '#E11D48'),
    ('Giai tri', 'EXPENSE', 'gamepad-2', '#DB2777'),
    ('Mua sam', 'EXPENSE', 'shopping-cart', '#0891B2'),
    ('Giao duc', 'EXPENSE', 'graduation-cap', '#0F766E')
ON CONFLICT DO NOTHING;
