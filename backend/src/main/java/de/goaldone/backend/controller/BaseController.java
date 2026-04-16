package de.goaldone.backend.controller;

import java.util.UUID;

public abstract class BaseController {

    protected UUID getCurrentUserId() {
        return null;
    }

    protected UUID getCurrentOrgId() {
        return null;
    }
}
