export const stationSections = [
    { id: 'general', label: 'General' },
    { id: 'links', label: 'Mail links' },
    { id: 'routing', label: 'Netmail routing' },
    { id: 'tosser', label: 'Tosser settings' },
    { id: 'echoes', label: 'Echo areas and link access' },
    { id: 'quarantine', label: 'Packet quarantine' },
    { id: 'logs', label: 'Logs' },
    { id: 'robots', label: 'Mail robots' },
    { id: 'accounts', label: 'Accounts and requests' },
    { id: 'plugins', label: 'Plugins' }
] as const;

export type StationSection = (typeof stationSections)[number]['id'];
