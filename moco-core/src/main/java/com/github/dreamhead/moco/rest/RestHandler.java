package com.github.dreamhead.moco.rest;

import com.github.dreamhead.moco.HttpRequest;
import com.github.dreamhead.moco.Moco;
import com.github.dreamhead.moco.MocoConfig;
import com.github.dreamhead.moco.MutableHttpResponse;
import com.github.dreamhead.moco.ResponseHandler;
import com.github.dreamhead.moco.RestSetting;
import com.github.dreamhead.moco.handler.AbstractHttpResponseHandler;
import com.github.dreamhead.moco.handler.JsonResponseHandler;
import com.github.dreamhead.moco.internal.SessionContext;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import io.netty.handler.codec.http.HttpResponseStatus;

import static com.github.dreamhead.moco.Moco.by;
import static com.github.dreamhead.moco.Moco.status;
import static com.github.dreamhead.moco.Moco.uri;
import static com.github.dreamhead.moco.util.URLs.join;
import static com.github.dreamhead.moco.util.URLs.resourceRoot;

public class RestHandler extends AbstractHttpResponseHandler {
    private final String name;
    private final RestSetting[] settings;
    private final ResponseHandler notFoundHandler;

    public RestHandler(final String name, final RestSetting... settings) {
        this.name = name;
        this.settings = settings;
        this.notFoundHandler = status(HttpResponseStatus.NOT_FOUND.code());
    }

    @Override
    protected void doWriteToResponse(final HttpRequest httpRequest, final MutableHttpResponse httpResponse) {
        if ("get".equalsIgnoreCase(httpRequest.getMethod())) {
            getGetHandler(httpRequest).writeToResponse(new SessionContext(httpRequest, httpResponse));
            return;
        }

        throw new UnsupportedOperationException("Unsupported REST request");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseHandler apply(final MocoConfig config) {
        if (config.isFor(MocoConfig.URI_ID)) {
            return new RestHandler((String) config.apply(name), settings);
        }

        return super.apply(config);
    }

    private ResponseHandler getGetHandler(final HttpRequest httpRequest) {
        FluentIterable<? extends GetRestSetting> restSettings = FluentIterable.of(settings)
                .filter(isGetHandler())
                .transform(toGetHandler());

        Optional<? extends GetRestSetting> matchedSetting = restSettings.firstMatch(matchSingle(httpRequest));
        if (matchedSetting.isPresent()) {
            return matchedSetting.get().getHandler();
        }

        if (by(uri(resourceRoot(name))).match(httpRequest)) {
            if (restSettings.allMatch(isJsonHandlers())) {
                ImmutableList<Object> objects = restSettings.transform(toJsonHandler()).transform(toPojo()).toList();
                return Moco.toJson(objects);
            }
        }

        return notFoundHandler;
    }

    private Predicate<GetRestSetting> matchSingle(final HttpRequest request) {
        return new Predicate<GetRestSetting>() {
            @Override
            public boolean apply(final GetRestSetting input) {
                return by(uri(join(resourceRoot(name), input.getId()))).match(request);
            }
        };
    }

    private Function<RestSetting, ? extends GetRestSetting> toGetHandler() {
        return new Function<RestSetting, GetRestSetting>() {
            @Override
            public GetRestSetting apply(final RestSetting setting) {
                return GetRestSetting.class.cast(setting);
            }
        };
    }

    private Predicate<RestSetting> isGetHandler() {
        return new Predicate<RestSetting>() {
            @Override
            public boolean apply(final RestSetting setting) {
                return setting instanceof GetRestSetting;
            }
        };
    }

    private Function<JsonResponseHandler, Object> toPojo() {
        return new Function<JsonResponseHandler, Object>() {
            @Override
            public Object apply(final JsonResponseHandler handler) {
                return handler.getPojo();
            }
        };
    }

    private Function<RestSetting, JsonResponseHandler> toJsonHandler() {
        return new Function<RestSetting, JsonResponseHandler>() {
            @Override
            public JsonResponseHandler apply(final RestSetting setting) {
                return JsonResponseHandler.class.cast(setting.getHandler());
            }
        };
    }

    private Predicate<RestSetting> isJsonHandlers() {
        return new Predicate<RestSetting>() {
            @Override
            public boolean apply(final RestSetting setting) {
                return setting.getHandler() instanceof JsonResponseHandler;
            }
        };
    }
}
