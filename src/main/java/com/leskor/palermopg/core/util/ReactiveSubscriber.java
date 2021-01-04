package com.leskor.palermopg.core.util;

import io.vertx.core.Promise;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Function;
import java.util.function.Predicate;

public class ReactiveSubscriber<T, R> implements Subscriber<T> {
    private final Promise<R> promise;
    private final Function<T, R> onResult;
    private final Predicate<T> successCriteria;
    private final boolean isVoid;
    private final R defaultValue;
    private final Throwable onFail;

    private Throwable error = null;
    private R result = null;
    private boolean succeeded;

    public static <T, R> ReactiveSubscriber<T, R> forSinglePromise(Promise<R> promise, Function<T, R> onResult) {
        return new ReactiveSubscriber<>(promise, onResult, null, false, null, null);
    }

    public static <T, R> ReactiveSubscriber<T, R> forSinglePromise(Promise<R> promise, Function<T, R> onResult, R defaultValue) {
        return new ReactiveSubscriber<>(promise, onResult, null, false, defaultValue, null);
    }

    public static <T, R> ReactiveSubscriber<T, R> forVoidPromise(Promise<R> promise, Predicate<T> successCriteria, Throwable onFail) {
        return new ReactiveSubscriber<>(promise, null, successCriteria, true, null, onFail);
    }

    protected ReactiveSubscriber(
            Promise<R> promise,
            Function<T, R> onResult,
            Predicate<T> successCriteria,
            boolean isVoid, R defaultValue,
            Throwable onFail
    ) {
        this.promise = promise;
        this.onResult = onResult;
        this.successCriteria = successCriteria;
        this.isVoid = isVoid;
        this.defaultValue = defaultValue;
        this.onFail = onFail;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(1);
    }

    @Override
    public void onNext(T next) {
        if (isVoid) {
            succeeded = successCriteria.test(next);
            return;
        }
        result = onResult.apply(next);
    }

    @Override
    public void onError(Throwable throwable) {
        error = throwable;
        promise.fail(throwable);
    }

    @Override
    public void onComplete() {
        if (error == null) {
            if (isVoid) {
                if (succeeded) {
                    promise.complete();
                } else {
                    promise.fail(onFail);
                }
                return;
            }
            promise.complete(result == null ? defaultValue : result);
        }
    }
}
