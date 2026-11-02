/**
 * The study document format as immutable records: the JSON files on disk, HTTP responses and
 * exports all use exactly these shapes. Records normalise missing values to the format's defaults
 * (empty strings, false, empty lists) so readers never see {@code null} where a value is required;
 * optional values stay {@code null} and are omitted from JSON. Structural rules live in {@link
 * com.petitesroues.documents.StudyDocumentValidator}, not in the records.
 */
package com.petitesroues.model.document;
