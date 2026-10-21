import type { ExtensionSettings } from '@rpc/bindings.mjs';

export function directoryExtensions(writable = true): ExtensionSettings[] {
    return [
        {
            id: 'nodelist',
            title: 'Node directory',
            source: 'conf.d/30-nodelist.conf',
            translations: {
                en: {
                    standard: 'Standard XMODEM',
                    'new-point-keeper': 'New Point Keeper (XMODEM / ARC)'
                },
                uk: {
                    'Node directory': 'Каталог вузлів',
                    'CRC checks': 'Перевірка CRC',
                    Source: 'Джерело',
                    'Filename mask': 'Маска імені файлу',
                    'CRC mode': 'Режим CRC',
                    standard: 'Стандартний XMODEM',
                    'new-point-keeper': 'New Point Keeper (XMODEM / ARC)'
                }
            },
            sections: [
                {
                    id: 'crc',
                    title: 'CRC checks',
                    writable,
                    fields: [
                        {
                            name: 'source',
                            label: 'Source',
                            type: 'select',
                            choices: ['nodes', 'points']
                        },
                        { name: 'mask', label: 'Filename mask', type: 'text', choices: [] },
                        {
                            name: 'mode',
                            label: 'CRC mode',
                            type: 'select',
                            choices: ['standard', 'new-point-keeper']
                        }
                    ],
                    rows: [{ source: 'points', mask: 'PNT46REG.*', mode: 'new-point-keeper' }]
                }
            ]
        }
    ];
}
