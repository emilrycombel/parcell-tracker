package com.example.parceltracker.web;

import com.example.parceltracker.db.ParcelRepository;
import com.example.parceltracker.model.Parcel;
import com.example.parceltracker.model.ParcelStatus;
import com.example.parceltracker.service.ParcelService;
import com.example.parceltracker.web.dto.Dtos.*;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ParcelEndpoint implements HttpService {

    private final ParcelService parcelService;

    public ParcelEndpoint(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.post("/parcels", this::register)
             .get("/parcels", this::list)
             .get("/parcels/{id}", this::getOne)
             .delete("/parcels/{id}", this::delete);
    }

    private void register(ServerRequest req, ServerResponse res) {
        try {
            RegisterParcelRequest body = req.content().as(RegisterParcelRequest.class);

            if (body.trackingNumber() == null || body.trackingNumber().isBlank() || body.deliveryAddress() == null) {
                res.status(Status.BAD_REQUEST_400).send(new ErrorResponse("trackingNumber and deliveryAddress are required"));
                return;
            }

            Parcel parcel = parcelService.register(
                    body.trackingNumber().trim(),
                    body.courier(),
                    body.deliveryAddress().toDomain()
            );
            res.status(Status.CREATED_201).send(ParcelResponse.from(parcel));
        } catch (ParcelRepository.DuplicateParcelException e) {
            res.status(Status.CONFLICT_409).send(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(new ErrorResponse("Registration failed: " + e.getMessage()));
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
            Optional<Parcel> refreshed = parcelService.getRefreshed(id);
            if (refreshed.isEmpty()) {
                res.status(Status.NOT_FOUND_404).send(new ErrorResponse("Parcel not found: " + id));
                return;
            }
            res.send(ParcelResponse.from(refreshed.get()));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(new ErrorResponse("Refresh failed: " + e.getMessage()));
        }
    }

    private void list(ServerRequest req, ServerResponse res) {
        int page = req.query().first("page").map(Integer::parseInt).orElse(0);
        int size = req.query().first("size").map(Integer::parseInt).orElse(20);
        ParcelStatus statusFilter = req.query().first("status").map(ParcelStatus::valueOf).orElse(null);

        List<ParcelResponse> parcels = parcelService.list(page, size, statusFilter).stream()
                .map(ParcelResponse::from)
                .toList();
        res.send(parcels);
    }

    private void delete(ServerRequest req, ServerResponse res) {
        UUID id;
        try {
            id = UUID.fromString(req.path().pathParameters().get("id"));
        } catch (IllegalArgumentException e) {
            res.status(Status.BAD_REQUEST_400).send(new ErrorResponse("Invalid parcel id"));
            return;
        }

        boolean deleted = parcelService.delete(id);
        res.status(deleted ? Status.NO_CONTENT_204 : Status.NOT_FOUND_404).send();
    }
}
