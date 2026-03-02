package io.strategiz.social.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchFilters {

	@JsonProperty("dateFilter")
	private DateFilter dateFilter;

	@JsonProperty("contentFilter")
	private ContentFilter contentFilter;

	@JsonProperty("mediaTypeFilter")
	private MediaTypeFilter mediaTypeFilter;

	public SearchFilters() {
	}

	public DateFilter getDateFilter() {
		return dateFilter;
	}

	public void setDateFilter(DateFilter dateFilter) {
		this.dateFilter = dateFilter;
	}

	public ContentFilter getContentFilter() {
		return contentFilter;
	}

	public void setContentFilter(ContentFilter contentFilter) {
		this.contentFilter = contentFilter;
	}

	public MediaTypeFilter getMediaTypeFilter() {
		return mediaTypeFilter;
	}

	public void setMediaTypeFilter(MediaTypeFilter mediaTypeFilter) {
		this.mediaTypeFilter = mediaTypeFilter;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class DateFilter {

		@JsonProperty("ranges")
		private List<DateRange> ranges;

		public DateFilter() {
		}

		public List<DateRange> getRanges() {
			return ranges;
		}

		public void setRanges(List<DateRange> ranges) {
			this.ranges = ranges;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class DateRange {

		@JsonProperty("startDate")
		private DateObj startDate;

		@JsonProperty("endDate")
		private DateObj endDate;

		public DateRange() {
		}

		public DateObj getStartDate() {
			return startDate;
		}

		public void setStartDate(DateObj startDate) {
			this.startDate = startDate;
		}

		public DateObj getEndDate() {
			return endDate;
		}

		public void setEndDate(DateObj endDate) {
			this.endDate = endDate;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class DateObj {

		@JsonProperty("year")
		private int year;

		@JsonProperty("month")
		private int month;

		@JsonProperty("day")
		private int day;

		public DateObj() {
		}

		public DateObj(int year, int month, int day) {
			this.year = year;
			this.month = month;
			this.day = day;
		}

		public int getYear() {
			return year;
		}

		public void setYear(int year) {
			this.year = year;
		}

		public int getMonth() {
			return month;
		}

		public void setMonth(int month) {
			this.month = month;
		}

		public int getDay() {
			return day;
		}

		public void setDay(int day) {
			this.day = day;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class ContentFilter {

		@JsonProperty("includedContentCategories")
		private List<String> includedContentCategories;

		public ContentFilter() {
		}

		public List<String> getIncludedContentCategories() {
			return includedContentCategories;
		}

		public void setIncludedContentCategories(List<String> includedContentCategories) {
			this.includedContentCategories = includedContentCategories;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class MediaTypeFilter {

		@JsonProperty("mediaTypes")
		private List<String> mediaTypes;

		public MediaTypeFilter() {
		}

		public List<String> getMediaTypes() {
			return mediaTypes;
		}

		public void setMediaTypes(List<String> mediaTypes) {
			this.mediaTypes = mediaTypes;
		}

	}

}
