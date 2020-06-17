package com.ruslanlesko.pichub.core.util;

import io.vertx.core.Promise;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ReactiveListSubscriber<T, R> implements Subscriber<T> {
    private static final Logger logger = LoggerFactory.getLogger("Application");

    private final Promise<List<R>> promise;
    private final Function<T, R> onResult;

    private Throwable error = null;
    private final List<R> result = new ArrayList<>();

    public static <T, R> ReactiveListSubscriber<T, R> forPromise(Promise<List<R>> promise, Function<T, R> onResult) {
        return new ReactiveListSubscriber<>(promise, onResult);
    }

    protected ReactiveListSubscriber(Promise<List<R>> promise, Function<T, R> onResult) {
        this.promise = promise;
        this.onResult = onResult;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        logger.debug("Subscribed..");
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T next) {
        logger.debug("Next arrived");
        result.add(onResult.apply(next));
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
            promise.complete(result);
        }
    }
}
