package com.example.parceltracker.adapter.in.web;

import com.example.parceltracker.adapter.in.web.dto.Dtos.ErrorResponse;
import com.example.parceltracker.adapter.in.web.dto.Dtos.ParcelResponse;
import com.example.parceltracker.adapter.in.web.dto.Dtos.RegisterParcelRequest;
import com.example.parceltracker.application.port.in.ParcelTrackingUseCase;
import com.example.parceltracker.application.port.out.DuplicateParcelException;
import com.example.parceltracker.domain.Parcel;
import com.example.parceltracker.domain.ParcelStatus;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driving adapter: HTTP surface over the {@link ParcelTrackingUseCase} port. */
public final class ParcelEndpoint implements HttpService {

    private static final Logger LOGGER = System.getLogger(ParcelEndpoint.class.getName());
    private static final int MAX_PAGE_SIZE = 200;

    private final ParcelTrackingUseCase parcelTracking;

    public ParcelEndpoint(ParcelTrackingUseCase parcelTracking) {
        this.parcelTracking = parcelTracking;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.post("/parcels", this::register)
             .get("/parcels", this::list)
             .get("/parcels/{id}", this::getOne)
             .delete("/parcels/{id}", this::delete);
    }

    private void register(ServerRequest req, ServerResponse res) {
        RegisterParcelRequest body;
        try {
            body = req.content().as(RegisterParcelRequest.class);
        } catch (Exception e) {
            // Malformed/unparseable JSON body — a client error (openapi documents 400).
            LOGGER.log(Level.DEBUG, "Rejected malformed register body", e);
            res.status(Status.BAD_REQUEST_400).send(new ErrorResponse("Malformed request body"));
            return;
        }

        if (body.trackingNumber() == null || body.trackingNumber().isBlank() || body.deliveryAddress() == null) {
            res.status(Status.BAD_REQUEST_400).send(new ErrorResponse("trackingNumber and deliveryAddress are required"));
            return;
        }

        try {
            Parcel parcel = parcelTracking.register(
                    body.trackingNumber().trim(),
                    body.courier(),
                    blankToNull(body.externalId()),
                    body.deliveryAddress().toDomain()
            );
            res.status(Status.CREATED_201).send(ParcelResponse.from(parcel));
        } catch (DuplicateParcelException e) {
            res.status(Status.CONFLICT_409).send(new ErrorResponse("A parcel with this tracking number and courier is already registered"));
        } catch (Exception e) {
            // Never leak internal exception text (may carry DB/driver detail) to the client.
            LOGGER.log(Level.ERROR, "Parcel registration failed", e);
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(new ErrorResponse("Registration failed"));
        }
    }

    private void getOne(ServerRequest req, ServerResponse res) {
        UUID id;
        try {
            id = UUID.fromString(req.path().pathParameters().get("id"));
        } catch (IllegalArgumentException e) {
            res.status(Status.BAD_REQUEST_400).send(new ErrorResponse("Invalid parcel id"));
            return;
        }

        try {
            Optional<Parcel> refreshed = parcelTracking.getRefreshed(id);
            if (refreshed.isEmpty()) {
                res.status(Status.NOT_FOUND_404).send(new ErrorResponse("Parcel not found: " + id));
                return;
            }
            res.send(ParcelResponse.from(refreshed.get()));
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Parcel refresh failed for " + id, e);
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(new ErrorResponse("Failed to refresh parcel"));
        }
    }

    private void list(ServerRequest req, ServerResponse res) {
        int page;
        int size;
        ParcelStatus statusFilter;
        try {
            page = req.query().first("page").map(Integer::parseInt).orElse(0);
            size = req.query().first("size").map(Integer::parseInt).orElse(20);
            statusFilter = req.query().first("status").map(ParcelStatus::valueOf).orElse(null);
        } catch (IllegalArgumentException e) {
            // NumberFormatException (page/size) and ParcelStatus.valueOf both extend this.
            res.status(Status.BAD_REQUEST_400).send(new ErrorResponse("Invalid query parameter (page, size, or status)"));
            return;
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            res.status(Status.BAD_REQUEST_400).send(
                    new ErrorResponse("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE));
            return;
        }
        String externalId = req.query().first("externalId").map(String::trim).filter(s -> !s.isEmpty()).orElse(null);

        List<ParcelResponse> parcels = parcelTracking.list(page, size, statusFilter, externalId).stream()
                .map(ParcelResponse::from)
                .toList();
        res.send(parcels);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void delete(ServerRequest req, ServerResponse res) {
        UUID id;
        try {
            id = UUID.fromString(req.path().pathParameters().get("id"));
        } catch (IllegalArgumentException e) {
            res.status(Status.BAD_REQUEST_400).send(new ErrorResponse("Invalid parcel id"));
            return;
        }

        boolean deleted = parcelTracking.delete(id);
        res.status(deleted ? Status.NO_CONTENT_204 : Status.NOT_FOUND_404).send();
    }
}
