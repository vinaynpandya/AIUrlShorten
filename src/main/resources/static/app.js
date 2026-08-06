(function () {
  "use strict";

  function toIsoUtc(datetimeLocalValue) {
    if (!datetimeLocalValue) {
      return null;
    }
    var localDate = new Date(datetimeLocalValue);
    if (isNaN(localDate.getTime())) {
      return null;
    }
    return localDate.toISOString();
  }

  function clearFieldErrors(prefix) {
    var fields = ["originalUrl", "customAlias", "expiresAt"];
    fields.forEach(function (field) {
      var el = document.getElementById(prefix + "-" + field);
      if (el) {
        el.textContent = "";
      }
    });
  }

  function setFormMessage(el, text, type) {
    el.textContent = text || "";
    el.classList.remove("error", "success");
    if (type) {
      el.classList.add(type);
    }
  }

  function applyApiError(messageEl, fieldErrorPrefix, status, errorBody) {
    var message = "Request failed.";
    if (errorBody && errorBody.message) {
      message = errorBody.message;
    } else if (status === 409) {
      message = "That alias is already in use.";
    } else if (status === 410) {
      message = "This short link has expired.";
    } else if (status === 404) {
      message = "Short code not found.";
    }
    setFormMessage(messageEl, status + ": " + message, "error");

    if (fieldErrorPrefix && errorBody && errorBody.fieldErrors) {
      Object.keys(errorBody.fieldErrors).forEach(function (field) {
        var el = document.getElementById(fieldErrorPrefix + "-" + field);
        if (el) {
          el.textContent = errorBody.fieldErrors[field];
        }
      });
    }
  }

  async function parseJsonSafe(response) {
    try {
      return await response.json();
    } catch (err) {
      return null;
    }
  }

  function formatInstant(value) {
    if (!value) {
      return "—";
    }
    return value;
  }

  // Create short URL
  var createForm = document.getElementById("create-form");
  var createMessage = document.getElementById("create-message");
  var createResult = document.getElementById("create-result");

  createForm.addEventListener("submit", async function (event) {
    event.preventDefault();
    setFormMessage(createMessage, "", null);
    clearFieldErrors("error");
    createResult.hidden = true;

    var originalUrl = document.getElementById("original-url").value.trim();
    var customAlias = document.getElementById("custom-alias").value.trim();
    var expiresAtLocal = document.getElementById("expires-at").value;

    var payload = {
      originalUrl: originalUrl
    };
    if (customAlias) {
      payload.customAlias = customAlias;
    }
    if (expiresAtLocal) {
      var isoExpiresAt = toIsoUtc(expiresAtLocal);
      if (!isoExpiresAt) {
        setFormMessage(createMessage, "Invalid expiration date/time.", "error");
        return;
      }
      payload.expiresAt = isoExpiresAt;
    }

    try {
      var response = await fetch("/api/v1/urls", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        var errorBody = await parseJsonSafe(response);
        applyApiError(createMessage, "error", response.status, errorBody);
        return;
      }

      var data = await response.json();

      document.getElementById("result-shortUrl").textContent = data.shortUrl;
      document.getElementById("result-shortCode").textContent = data.shortCode;
      document.getElementById("result-createdAt").textContent = formatInstant(data.createdAt);
      document.getElementById("result-expiresAt").textContent = formatInstant(data.expiresAt);

      var openLink = document.getElementById("open-link");
      openLink.href = data.shortUrl;

      createResult.hidden = false;
      setFormMessage(createMessage, "Short URL created.", "success");
    } catch (err) {
      setFormMessage(createMessage, "Network error while creating the short URL.", "error");
    }
  });

  var copyButton = document.getElementById("copy-button");
  copyButton.addEventListener("click", async function () {
    var shortUrl = document.getElementById("result-shortUrl").textContent;
    if (!shortUrl) {
      return;
    }
    try {
      await navigator.clipboard.writeText(shortUrl);
      var original = copyButton.textContent;
      copyButton.textContent = "Copied!";
      setTimeout(function () {
        copyButton.textContent = original;
      }, 1500);
    } catch (err) {
      setFormMessage(createMessage, "Could not copy to clipboard.", "error");
    }
  });

  // Analytics lookup
  var analyticsForm = document.getElementById("analytics-form");
  var analyticsMessage = document.getElementById("analytics-message");
  var analyticsResult = document.getElementById("analytics-result");

  analyticsForm.addEventListener("submit", async function (event) {
    event.preventDefault();
    setFormMessage(analyticsMessage, "", null);
    analyticsResult.hidden = true;

    var shortCode = document.getElementById("analytics-short-code").value.trim();
    if (!shortCode) {
      setFormMessage(analyticsMessage, "Please enter a short code.", "error");
      return;
    }

    try {
      var response = await fetch("/api/v1/urls/" + encodeURIComponent(shortCode) + "/analytics");

      if (!response.ok) {
        var errorBody = await parseJsonSafe(response);
        applyApiError(analyticsMessage, null, response.status, errorBody);
        return;
      }

      var data = await response.json();

      document.getElementById("analytics-originalUrl").textContent = data.originalUrl;
      document.getElementById("analytics-clickCount").textContent = String(data.clickCount);
      document.getElementById("analytics-createdAt").textContent = formatInstant(data.createdAt);
      document.getElementById("analytics-lastAccessedAt").textContent = formatInstant(data.lastAccessedAt);
      document.getElementById("analytics-expiresAt").textContent = formatInstant(data.expiresAt);

      analyticsResult.hidden = false;
      setFormMessage(analyticsMessage, "", null);
    } catch (err) {
      setFormMessage(analyticsMessage, "Network error while fetching analytics.", "error");
    }
  });

  // Click analytics
  var clickAnalyticsForm = document.getElementById("click-analytics-form");
  var clickAnalyticsMessage = document.getElementById("click-analytics-message");
  var clickAnalyticsResult = document.getElementById("click-analytics-result");

  function renderBreakdown(listEl, breakdown) {
    listEl.innerHTML = "";
    var entries = Object.keys(breakdown || {});
    if (entries.length === 0) {
      var emptyItem = document.createElement("li");
      emptyItem.className = "breakdown-empty";
      emptyItem.textContent = "No data yet.";
      listEl.appendChild(emptyItem);
      return;
    }
    entries.sort(function (a, b) {
      return breakdown[b] - breakdown[a];
    });
    entries.forEach(function (key) {
      var item = document.createElement("li");
      item.className = "breakdown-item";
      var nameSpan = document.createElement("span");
      nameSpan.textContent = key;
      var countSpan = document.createElement("span");
      countSpan.className = "breakdown-count";
      countSpan.textContent = String(breakdown[key]);
      item.appendChild(nameSpan);
      item.appendChild(countSpan);
      listEl.appendChild(item);
    });
  }

  clickAnalyticsForm.addEventListener("submit", async function (event) {
    event.preventDefault();
    setFormMessage(clickAnalyticsMessage, "", null);
    clickAnalyticsResult.hidden = true;

    var shortCode = document.getElementById("click-analytics-short-code").value.trim();
    if (!shortCode) {
      setFormMessage(clickAnalyticsMessage, "Please enter a short code.", "error");
      return;
    }

    try {
      var response = await fetch("/api/v1/urls/" + encodeURIComponent(shortCode) + "/click-analytics");

      if (!response.ok) {
        var errorBody = await parseJsonSafe(response);
        applyApiError(clickAnalyticsMessage, null, response.status, errorBody);
        return;
      }

      var data = await response.json();

      document.getElementById("click-analytics-totalEvents").textContent = String(data.totalEvents);
      renderBreakdown(document.getElementById("click-analytics-byBrowser"), data.byBrowser);

      clickAnalyticsResult.hidden = false;
      setFormMessage(clickAnalyticsMessage, "", null);
    } catch (err) {
      setFormMessage(clickAnalyticsMessage, "Network error while fetching click analytics.", "error");
    }
  });

  // Health check
  var healthButton = document.getElementById("health-check-button");
  var healthMessage = document.getElementById("health-message");
  var healthResult = document.getElementById("health-result");

  healthButton.addEventListener("click", async function () {
    setFormMessage(healthMessage, "", null);
    healthResult.hidden = true;

    try {
      var response = await fetch("/actuator/health");
      var data = await parseJsonSafe(response);
      var status = data && data.status ? data.status : "UNKNOWN";

      document.getElementById("health-status").textContent = status;
      healthResult.hidden = false;

      if (status === "UP") {
        setFormMessage(healthMessage, "Service is healthy.", "success");
      } else {
        setFormMessage(healthMessage, "Service is not healthy.", "error");
      }
    } catch (err) {
      setFormMessage(healthMessage, "Network error while checking health.", "error");
    }
  });
})();
