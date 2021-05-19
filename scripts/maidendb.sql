drop schema maidendb cascade;

create schema maidendb;

-- Tables
create table maidendb.GuildData
(
    guild_id      bigint primary key not null,

    phone_number  varchar(16) null,
    phone_channel bigint null
);

create table maidendb.GuildScheduledEvent
(
    event_id       integer primary key generated always as identity not null,

    guild_id       bigint                                           not null,
    channel_id     bigint                                           not null,
    requester_id   bigint                                           not null,

    start_at       timestamp without time zone not null,
    interval_s     bigint null,

    command_string text                                             not null
);
