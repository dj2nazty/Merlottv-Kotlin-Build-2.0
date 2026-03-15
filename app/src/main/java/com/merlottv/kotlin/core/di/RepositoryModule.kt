package com.merlottv.kotlin.core.di

import com.merlottv.kotlin.data.repository.AuthRepositoryImpl
import com.merlottv.kotlin.data.repository.DeviceCodeRepositoryImpl
import com.merlottv.kotlin.data.repository.AddonRepositoryImpl
import com.merlottv.kotlin.data.repository.ChannelRepositoryImpl
import com.merlottv.kotlin.data.repository.EpgRepositoryImpl
import com.merlottv.kotlin.data.repository.EspnRepositoryImpl
import com.merlottv.kotlin.data.repository.FavoritesRepositoryImpl
import com.merlottv.kotlin.data.repository.SpaceXRepositoryImpl
import com.merlottv.kotlin.data.repository.WeatherRepositoryImpl
import com.merlottv.kotlin.domain.repository.AuthRepository
import com.merlottv.kotlin.domain.repository.DeviceCodeRepository
import com.merlottv.kotlin.domain.repository.AddonRepository
import com.merlottv.kotlin.domain.repository.ChannelRepository
import com.merlottv.kotlin.domain.repository.EpgRepository
import com.merlottv.kotlin.domain.repository.EspnRepository
import com.merlottv.kotlin.domain.repository.FavoritesRepository
import com.merlottv.kotlin.domain.repository.SpaceXRepository
import com.merlottv.kotlin.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindDeviceCodeRepository(impl: DeviceCodeRepositoryImpl): DeviceCodeRepository

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds
    @Singleton
    abstract fun bindEpgRepository(impl: EpgRepositoryImpl): EpgRepository

    @Binds
    @Singleton
    abstract fun bindAddonRepository(impl: AddonRepositoryImpl): AddonRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindEspnRepository(impl: EspnRepositoryImpl): EspnRepository

    @Binds
    @Singleton
    abstract fun bindSpaceXRepository(impl: SpaceXRepositoryImpl): SpaceXRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository
}
