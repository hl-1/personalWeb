ALTER TABLE content_category
    ADD CONSTRAINT uk_content_category_name UNIQUE (name);

ALTER TABLE content_tag
    ADD CONSTRAINT uk_content_tag_name UNIQUE (name);
