/**
 * Live sync: tells the open apps of a family that something changed, so they refetch (D1). A notice carries an entity
 * name and at most an id, never a value. Nothing depends on this module.
 */
@ApplicationModule(
        allowedDependencies = {"identity", "workspace", "ledger", "planning", "gamification", "ventures", "shared"})
package br.com.liberdadefamilia.live;

import org.springframework.modulith.ApplicationModule;
