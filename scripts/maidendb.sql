drop schema maidendb cascade;

create schema maidendb;

-- Tables
create table maidendb.GuildData
(
    guild_id      bigint primary key not null,

    phone_number  varchar(16) null,
    phone_channel bigint null
);
