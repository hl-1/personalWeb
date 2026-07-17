CREATE TABLE content_category (
    id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_content_category PRIMARY KEY (id),
    CONSTRAINT uk_content_category_slug UNIQUE (slug),
    CONSTRAINT ck_content_category_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_content_category_slug CHECK (
        char_length(slug) BETWEEN 3 AND 120
        AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_content_category_version CHECK (version >= 0)
);

CREATE TABLE content_tag (
    id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_content_tag PRIMARY KEY (id),
    CONSTRAINT uk_content_tag_slug UNIQUE (slug),
    CONSTRAINT ck_content_tag_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_content_tag_slug CHECK (
        char_length(slug) BETWEEN 3 AND 120
        AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_content_tag_version CHECK (version >= 0)
);

CREATE TABLE content_article (
    id UUID NOT NULL,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(180) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    body_markdown VARCHAR(200000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    category_id UUID,
    seo_title VARCHAR(70),
    seo_description VARCHAR(160),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_content_article PRIMARY KEY (id),
    CONSTRAINT uk_content_article_slug UNIQUE (slug),
    CONSTRAINT ck_content_article_slug CHECK (
        char_length(slug) BETWEEN 3 AND 120
        AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_content_article_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_content_article_summary_not_blank CHECK (btrim(summary) <> ''),
    CONSTRAINT ck_content_article_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_content_article_publication CHECK (
        status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CONSTRAINT ck_content_article_version CHECK (version >= 0),
    CONSTRAINT fk_content_article_category
        FOREIGN KEY (category_id) REFERENCES content_category(id) ON DELETE SET NULL
);

CREATE TABLE content_article_tag (
    article_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    CONSTRAINT pk_content_article_tag PRIMARY KEY (article_id, tag_id),
    CONSTRAINT fk_content_article_tag_article
        FOREIGN KEY (article_id) REFERENCES content_article(id) ON DELETE CASCADE,
    CONSTRAINT fk_content_article_tag_tag
        FOREIGN KEY (tag_id) REFERENCES content_tag(id) ON DELETE CASCADE
);

CREATE INDEX ix_content_article_publication
    ON content_article (status, published_at DESC, id DESC);

CREATE INDEX ix_content_article_category_publication
    ON content_article (category_id, status, published_at DESC, id DESC);

CREATE INDEX ix_content_article_tag_tag_article
    ON content_article_tag (tag_id, article_id);
