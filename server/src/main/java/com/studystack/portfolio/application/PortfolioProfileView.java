package com.studystack.portfolio.application;

public record PortfolioProfileView(
        String displayName,
        String headline,
        String bioHtml,
        String seoDescription) {
}
