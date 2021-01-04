package com.leskor.palermopg.core.util;

import io.vertx.core.Promise;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ReactiveListSubscriber<T, R> implements Subscriber<T> {
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
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T next) {
        result.add(onResult.apply(next));
    }

    @Override
    public void onError(Throwable throwable) {
        error = throwable;
        promise.fail(throwable);
    }

    @Override
    public void onComplete() {
        if (error == null) {
            promise.complete(result);
        }
    }
}
