import type { ExtensionSettings } from '@rpc/bindings.mjs';

export function directoryExtensions(writable = true): ExtensionSettings[] {
    return [
        {
            id: 'nodelist',
            title: 'Node directory',
            source: 'conf.d/30-nodelist.conf',
            translations: {},
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
                            choices: ['XMODEM-CRC', 'ARC-CRC']
                        }
                    ],
                    rows: [{ source: 'points', mask: 'PNT46REG.*', mode: 'ARC-CRC' }]
                }
            ]
        }
    ];
}
