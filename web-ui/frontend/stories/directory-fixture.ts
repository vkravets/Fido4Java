import type { DirectoryNode } from '@rpc/bindings.mjs';
import type { DirectorySearch } from '../src/directory.ts';
export const directoryNodes: DirectoryNode[] = [
    {
        address: '1:500/101.0',
        source: 'nodelist',
        kind: 'NODE',
        system: 'Kyiv Mail Station',
        sysop: 'Jane Doe',
        location: 'Kyiv, Ukraine',
        phone: '-Unpublished-',
        endpoints: ['192.0.2.10:24554'],
        flags: ['CM', 'INA:192.0.2.10', 'IBN']
    },
    {
        address: '1:500/101.1',
        source: 'pointlist',
        kind: 'POINT',
        system: 'First Point',
        sysop: 'Point Owner',
        location: 'Lviv, Ukraine',
        phone: '-Unpublished-',
        endpoints: [],
        flags: ['MO']
    },
    {
        address: '1:500/102.0',
        source: 'nodelist',
        kind: 'NODE',
        system: 'Very Long Station Name With Additional Information',
        sysop: 'Alex Example',
        location: 'Odesa, Ukraine',
        phone: '-Unpublished-',
        endpoints: ['[2001:db8::1]:24555', 'mail.example.org:24554'],
        flags: ['CM', 'IBN', 'INA:[2001:db8::1]']
    }
];
export const searchDirectory: DirectorySearch = async (query, offset, limit) => {
    const matches = directoryNodes.filter((node) =>
        JSON.stringify(node).toLowerCase().includes(query.toLowerCase())
    );
    return {
        items: matches.slice(offset, offset + limit),
        nextOffset: offset + Math.min(limit, matches.length - offset),
        hasMore: offset + limit < matches.length
    };
};
