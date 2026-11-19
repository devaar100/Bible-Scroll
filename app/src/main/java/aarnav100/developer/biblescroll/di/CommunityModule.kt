package aarnav100.developer.biblescroll.di

import aarnav100.developer.biblescroll.BuildConfig
import aarnav100.developer.biblescroll.data.community.CommunityConfig
import aarnav100.developer.biblescroll.data.community.VoteSendScheduler
import aarnav100.developer.biblescroll.data.community.WorkManagerVoteSendScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityModule {
    @Binds
    abstract fun bindVoteSendScheduler(impl: WorkManagerVoteSendScheduler): VoteSendScheduler

    companion object {
        /** test:true ministries appear in debug builds only. */
        @Provides
        fun provideCommunityConfig(): CommunityConfig = CommunityConfig(includeTest = BuildConfig.DEBUG)
    }
}
