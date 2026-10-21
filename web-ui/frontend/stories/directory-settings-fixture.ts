import type { ExtensionSettings } from '@rpc/bindings.mjs';

export function directoryExtensions(writable = true): ExtensionSettings[] {
    return [
        {
            id: 'nodelist',
            title: 'Node directory',
            source: 'conf.d/30-nodelist.conf',
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
                },
                {
                    id: 'sources',
                    title: 'List sources',
                    writable,
                    fields: [
                        { name: 'id', label: 'Source ID', type: 'text', choices: [] },
                        {
                            name: 'format',
                            label: 'List format',
                            type: 'select',
                            choices: [
                                'NODELIST',
                                'BOSS',
                                'POINT',
                                'POSS',
                                'FAKENET',
                                'FIDOUSER',
                                'FIDOUSER_TEXT'
                            ]
                        },
                        { name: 'path', label: 'List file', type: 'text', choices: [] },
                        { name: 'zone', label: 'Default zone', type: 'text', choices: [] },
                        { name: 'net', label: 'Default net', type: 'text', choices: [] },
                        { name: 'charset', label: 'List encoding', type: 'text', choices: [] },
                        {
                            name: 'requireChecksum',
                            label: 'Require header CRC',
                            type: 'select',
                            choices: ['true', 'false']
                        }
                    ],
                    rows: [
                        {
                            id: 'nodes',
                            format: 'NODELIST',
                            path: './lists/nodes.lst',
                            zone: '0',
                            net: '0',
                            charset: 'IBM866',
                            requireChecksum: 'true'
                        },
                        {
                            id: 'points',
                            format: 'BOSS',
                            path: './lists/points.lst',
                            zone: '0',
                            net: '0',
                            charset: 'IBM866',
                            requireChecksum: 'true'
                        }
                    ]
                }
            ]
        }
    ];
}
