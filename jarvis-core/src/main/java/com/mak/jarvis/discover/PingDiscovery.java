package com.mak.jarvis.discover;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.*;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.sun.istack.internal.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Discover items by scanning the LAN for accessible addresses
 */
public class PingDiscovery {
  // set of all subnet addresses that we can find
  private static final ContiguousSet<Integer> SUBNET_ADDRESSES =
          ContiguousSet.create(Range.closed(1, 254), DiscreteDomain.integers());
  // this will effectively determine the return time for the discover method. We won't be returning any faster than
  // this
  private static final int REACHABLE_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(10);

  public static void main(String[] args) throws IOException {
    Injector injector = Guice.createInjector(new PingDiscoveryModule());
    PingDiscovery discovery = injector.getInstance(PingDiscovery.class);

    Stopwatch timer = Stopwatch.createStarted();
    System.out.println("Found addresses " + discovery.discover() + " in " + timer);
  }

  private final ExecutorService executor;

  @Inject
  public PingDiscovery(ExecutorService executor) {
    this.executor = executor;
  }

  public ImmutableSet<String> discover() throws IOException {
    return FluentIterable.from(addresses())
            .transform(new Function<InetAddress, String>() {
              @Override
              public String apply(InetAddress input) {
                return input.getHostAddress();
              }
            })
            .toSet();
  }

  private ImmutableSet<InetAddress> addresses() throws IOException {
    return addresses("192.168.0");
  }

  private ImmutableSet<InetAddress> addresses(String subnet) throws IOException {
    Collection<Future<Optional<InetAddress>>> futures = Lists.newArrayListWithExpectedSize(SUBNET_ADDRESSES.size());
    for (Integer subnetAddress : SUBNET_ADDRESSES) {
      futures.add(executor.submit(new ReachableTask(subnet + "." + subnetAddress)));
    }

    Set<InetAddress> reachableAddresses = Sets.newConcurrentHashSet();
    for (Future<Optional<InetAddress>> future : futures) {
      try {
        Optional<InetAddress> optional = future.get();
        if (optional.isPresent()) {
          reachableAddresses.add(optional.get());
        }
      } catch (InterruptedException e) {
        // ignore it and process the next future. We just won't have the item in the collection at the end
      } catch (ExecutionException e) {
        Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
        throw new RuntimeException(e);
      }
    }
    return ImmutableSet.copyOf(reachableAddresses);
  }

  /**
   * Task that checks the given InetAddress for reachability returning Optional.absent if it can't be reached.
   */
  private static class ReachableTask implements Callable<Optional<InetAddress>> {

    private final String addressName;

    private ReachableTask(String addressName) {
      this.addressName = checkNotNull(addressName);
    }

    @Override
    @Nullable
    public Optional<InetAddress> call() throws Exception {
      InetAddress address = InetAddress.getByName(addressName);
      Stopwatch timer = Stopwatch.createStarted();
      if (address.isReachable(REACHABLE_TIMEOUT)) {
        System.out.println("Address " + address + " REACHABLE in " + timer);
        return Optional.of(address);
      } else {
        System.out.println("Address " + address + " UNREACHABLE in " + timer);
        return Optional.absent();
      }
    }
  }
}
