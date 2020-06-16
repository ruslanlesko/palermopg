package com.ruslanlesko.pichub.core.dao.util;

import io.vertx.core.Promise;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class ReactiveSubscriber<T, R> implements Subscriber<T> {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final Promise<R> promise;
    private final Function<T, R> onResult;
    private final R defaultValue;

    private Throwable error = null;
    private R result = null;

    public static <T, R> ReactiveSubscriber<T, R> forPromise(Promise<R> promise, Function<T, R> onResult) {
        return new ReactiveSubscriber<>(promise, onResult, null);
    }

    public static <T, R> ReactiveSubscriber<T, R> forPromise(Promise<R> promise, Function<T, R> onResult, R defaultValue) {
        return new ReactiveSubscriber<>(promise, onResult, defaultValue);
    }

    protected ReactiveSubscriber(Promise<R> promise, Function<T, R> onResult, R defaultValue) {
        this.promise = promise;
        this.onResult = onResult;
        this.defaultValue = defaultValue;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(1);
    }

    @Override
    public void onNext(T next) {
        logger.debug("Next arrived");
        result = onResult.apply(next);
    }

    @Override
    public void onError(Throwable throwable) {
        logger.debug("Error arrived");
        error = throwable;
        promise.fail(throwable);
    }

    @Override
    public void onComplete() {
        logger.debug("Completing...");
        if (error == null) {
            promise.complete(result == null ? defaultValue : result);
        }
    }
}
