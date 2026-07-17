CREATE TABLE portfolio_profile (
    id INTEGER NOT NULL DEFAULT 1,
    display_name VARCHAR(120) NOT NULL,
    headline VARCHAR(180) NOT NULL,
    bio_markdown VARCHAR(50000) NOT NULL,
    seo_description VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_portfolio_profile PRIMARY KEY (id),
    CONSTRAINT ck_portfolio_profile_singleton CHECK (id = 1),
    CONSTRAINT ck_portfolio_profile_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_portfolio_profile_headline_not_blank CHECK (btrim(headline) <> ''),
    CONSTRAINT ck_portfolio_profile_version CHECK (version >= 0)
);

CREATE TABLE portfolio_project (
    id UUID NOT NULL,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(180) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    description_markdown VARCHAR(100000) NOT NULL,
    project_url VARCHAR(2048),
    repository_url VARCHAR(2048),
    status VARCHAR(16) NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_portfolio_project PRIMARY KEY (id),
    CONSTRAINT uk_portfolio_project_slug UNIQUE (slug),
    CONSTRAINT ck_portfolio_project_slug CHECK (
        char_length(slug) BETWEEN 3 AND 120
        AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_portfolio_project_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_portfolio_project_summary_not_blank CHECK (btrim(summary) <> ''),
    CONSTRAINT ck_portfolio_project_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_portfolio_project_publication CHECK (
        status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CONSTRAINT ck_portfolio_project_project_url CHECK (
        project_url IS NULL
        OR project_url ~ '^https://[^[:space:]@/?#]+([/?#][^[:space:]]*)?$'),
    CONSTRAINT ck_portfolio_project_repository_url CHECK (
        repository_url IS NULL
        OR repository_url ~ '^https://[^[:space:]@/?#]+([/?#][^[:space:]]*)?$'),
    CONSTRAINT ck_portfolio_project_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_portfolio_project_version CHECK (version >= 0)
);

CREATE TABLE portfolio_skill (
    id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(120) NOT NULL,
    summary VARCHAR(500),
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_portfolio_skill PRIMARY KEY (id),
    CONSTRAINT ck_portfolio_skill_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_portfolio_skill_category_not_blank CHECK (btrim(category) <> ''),
    CONSTRAINT ck_portfolio_skill_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_portfolio_skill_version CHECK (version >= 0)
);

CREATE TABLE portfolio_experience (
    id UUID NOT NULL,
    organization VARCHAR(180) NOT NULL,
    role VARCHAR(180) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    summary_markdown VARCHAR(20000) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_portfolio_experience PRIMARY KEY (id),
    CONSTRAINT ck_portfolio_experience_organization_not_blank CHECK (btrim(organization) <> ''),
    CONSTRAINT ck_portfolio_experience_role_not_blank CHECK (btrim(role) <> ''),
    CONSTRAINT ck_portfolio_experience_date_range CHECK (
        end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_portfolio_experience_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_portfolio_experience_version CHECK (version >= 0)
);

CREATE INDEX ix_portfolio_project_publication
    ON portfolio_project (status, published_at DESC, id DESC);

CREATE INDEX ix_portfolio_skill_visible_order
    ON portfolio_skill (visible, sort_order, id);

CREATE INDEX ix_portfolio_experience_visible_order
    ON portfolio_experience (visible, sort_order, start_date DESC, id);
