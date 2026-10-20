import type { InjectionKey } from 'vue';
import type { ProfileServiceClient, UiServiceClient } from '@rpc/bindings.mjs';

export type RobotAccessClient = Pick<
    UiServiceClient,
    'robotAccess' | 'saveRobotRule' | 'deleteRobotRule'
>;
export type AccountModerationClient = Pick<ProfileServiceClient, 'accounts' | 'decideAka'>;

export const robotAccessClientKey: InjectionKey<RobotAccessClient> = Symbol('robot-access-client');
export const accountModerationClientKey: InjectionKey<AccountModerationClient> = Symbol(
    'account-moderation-client'
);
