import type { ExtensionSettings } from '@rpc/bindings.mjs';

export function fileEchoExtensions(): ExtensionSettings[] {
    const text = (name: string, label: string) => ({ name, label, type: 'text', choices: [] });
    const choice = (name: string, label: string, choices: string[]) => ({
        name,
        label,
        type: 'select',
        choices
    });
    const peer = () => choice('peer', 'Link', ['1:500/101.0', '1:500/102.0']);
    return [
        {
            id: 'fileecho',
            title: 'File echoes',
            source: 'database',
            translations: {
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
            sections: [
                {
                    id: 'areas',
                    title: 'File areas',
                    writable: true,
                    fields: [
                        text('name', 'Area'),
                        text('directory', 'Directory'),
                        text('description', 'Description'),
                        choice('enabled', 'Enabled', ['true', 'false'])
                    ],
                    rows: [
                        {
                            name: 'lists.nodes',
                            directory: './fileecho/lists.nodes',
                            description: 'Node and point lists',
                            enabled: 'true'
                        },
                        {
                            name: 'local.tools',
                            directory: './fileecho/local.tools',
                            description: 'Station utilities',
                            enabled: 'false'
                        }
                    ]
                },
                {
                    id: 'access',
                    title: 'File area access',
                    writable: true,
                    fields: [
                        choice('name', 'Area', ['lists.nodes', 'local.tools']),
                        peer(),
                        choice('access', 'Access', ['READ', 'WRITE', 'BOTH', 'NONE'])
                    ],
                    rows: [{ name: 'lists.nodes', peer: '1:500/101.0', access: 'WRITE' }]
                },
                {
                    id: 'creation',
                    title: 'Automatic file area creation',
                    writable: true,
                    fields: [
                        peer(),
                        text('masks', 'Area masks'),
                        choice('access', 'Access', ['WRITE', 'BOTH', 'NONE'])
                    ],
                    rows: [{ peer: '1:500/101.0', masks: 'lists.*,local.*', access: 'WRITE' }]
                },
                {
                    id: 'robots',
                    title: 'FileFix and TIC access',
                    writable: true,
                    fields: [
                        choice('service', 'Service', ['FileFix', 'Tic']),
                        peer(),
                        { name: 'password', label: 'Password', type: 'password', choices: [] },
                        text('masks', 'Area masks'),
                        choice('enabled', 'Enabled', ['true', 'false'])
                    ],
                    rows: [
                        {
                            service: 'Tic',
                            peer: '1:500/101.0',
                            password: '',
                            masks: 'lists.*',
                            enabled: 'true'
                        }
                    ]
                }
            ]
        }
    ];
}
