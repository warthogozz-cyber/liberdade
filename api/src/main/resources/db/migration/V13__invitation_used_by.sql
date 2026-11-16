-- Who took an invitation, so that the same person repeating the request gets the same answer. Links used before this
-- migration keep a null here and go on answering 410.
alter table invitations
    add column used_by uuid null references users (id) on delete set null;

create index invitations_used_by_idx on invitations (used_by);
