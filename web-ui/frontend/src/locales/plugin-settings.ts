// Settings metadata is localized by the browser, never by backend providers.
export const pluginSettingsTranslations: Record<string, Record<string, Record<string, string>>> = {
    fileecho: {
        en: {
            true: 'Yes',
            false: 'No',
            READ: 'Receives from us',
            WRITE: 'Sends to us',
            BOTH: 'Both directions',
            NONE: 'No access'
        },
        uk: {
            'File echoes': 'Файлові конференції',
            'File areas': 'Файлові області',
            'File area access': 'Доступ до файлових областей',
            'Automatic file area creation': 'Автоматичне створення файлових областей',
            'FileFix and TIC access': 'Доступ до FileFix і TIC',
            Area: 'Область',
            Directory: 'Каталог',
            Description: 'Опис',
            Enabled: 'Увімкнено',
            Link: 'Лінк',
            Access: 'Доступ',
            'Area masks': 'Маски областей',
            Service: 'Служба',
            Password: 'Пароль',
            true: 'Так',
            false: 'Ні',
            READ: 'Отримує від нас',
            WRITE: 'Надсилає нам',
            BOTH: 'Обидва напрямки',
            NONE: 'Без доступу'
        }
    },
    nodelist: {
        en: {
            true: 'Yes',
            false: 'No',
            'XMODEM-CRC': 'XMODEM-CRC',
            'ARC-CRC': 'ARC-CRC'
        },
        uk: {
            'Node directory': 'Каталог вузлів',
            'List sources': 'Джерела списків',
            'Source ID': 'ID джерела',
            'List format': 'Формат списку',
            'List file': 'Файл списку',
            'Default zone': 'Типова зона',
            'Default net': 'Типова мережа',
            'List encoding': 'Кодування списку',
            'Require header CRC': 'Вимагати CRC заголовка',
            true: 'Так',
            false: 'Ні',
            'CRC checks': 'Перевірка CRC',
            'Legacy CRC exceptions': 'Старі винятки перевірки CRC',
            Source: 'Джерело',
            'Filename mask': 'Маска імені файлу',
            'CRC mode': 'Режим CRC',
            'XMODEM-CRC': 'XMODEM-CRC',
            'ARC-CRC': 'ARC-CRC'
        }
    }
};
