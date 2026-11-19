package aarnav100.developer.biblescroll.di

import aarnav100.developer.biblescroll.widget.AlarmVotdWidgetScheduler
import aarnav100.developer.biblescroll.widget.AndroidHomeScreenWidgets
import aarnav100.developer.biblescroll.widget.HomeScreenWidgets
import aarnav100.developer.biblescroll.widget.VotdWidgetScheduler
import aarnav100.developer.biblescroll.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    abstract fun bindHomeScreenWidgets(impl: AndroidHomeScreenWidgets): HomeScreenWidgets

    @Binds
    abstract fun bindWidgetRefresher(impl: AndroidHomeScreenWidgets): WidgetRefresher

    @Binds
    abstract fun bindVotdWidgetScheduler(impl: AlarmVotdWidgetScheduler): VotdWidgetScheduler
}
