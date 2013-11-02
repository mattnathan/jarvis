package com.mak.jarvis.discover;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.concurrent.*;

/**
 * Created by Matt on 02/11/13.
 */
public class PingDiscoveryModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Executor.class).to(ExecutorService.class);
  }

  @Provides
  ExecutorService provideExecutorService() {
    // by setting both the core and max sizes to the same value and allowing core threads to timeout we effectively
    // change the default behaviour of the executor from favouring the queue to favouring creation of new threads while
    // maintaining the semantics of timing out the threads when they aren't being used.
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
            256, 256, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactoryBuilder().setDaemon(true).build());
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }
}
