document.addEventListener('DOMContentLoaded', function() {
    console.log('Weather Dashboard loaded');

    // Auto-dismiss alerts after 5 seconds
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            if (alert.classList.contains('show')) {
                alert.classList.remove('show');
                setTimeout(() => alert.remove(), 150);
            }
        }, 5000);
    });

    // Initialize city search if input exists
    const citySearchInput = document.getElementById('citySearch');
    if (citySearchInput) {
        initializeCitySearch();
    }
});

// City search functionality
function initializeCitySearch() {
    const searchInput = document.getElementById('citySearch');
    const resultsDiv = document.getElementById('searchResults');
    let searchTimeout;

    searchInput.addEventListener('input', function() {
        const query = this.value.trim();

        // Clear previous timeout
        clearTimeout(searchTimeout);

        if (query.length < 3) {
            resultsDiv.innerHTML = '';
            return;
        }

        // Debounce search requests
        searchTimeout = setTimeout(() => {
            searchCities(query);
        }, 300);
    });

    // Clear results when clicking outside
    document.addEventListener('click', function(e) {
        if (!searchInput.contains(e.target) && !resultsDiv.contains(e.target)) {
            resultsDiv.innerHTML = '';
        }
    });
}

// Search cities using API
function searchCities(query) {
    const resultsDiv = document.getElementById('searchResults');

    // Show loading
    resultsDiv.innerHTML = '<div class="text-center py-2"><i class="fas fa-spinner fa-spin"></i> Searching...</div>';

    fetch(`/dashboard/search?query=${encodeURIComponent(query)}`)
        .then(response => response.json())
        .then(cities => {
            displaySearchResults(cities);
        })
        .catch(error => {
            console.error('Search error:', error);
            resultsDiv.innerHTML = '<div class="text-danger small">Search failed. Please try again.</div>';
        });
}

// Display search results
function displaySearchResults(cities) {
    const resultsDiv = document.getElementById('searchResults');

    if (!cities || cities.length === 0) {
        resultsDiv.innerHTML = '<div class="text-muted small">No cities found</div>';
        return;
    }

    let html = '<div class="list-group list-group-flush">';

    cities.forEach(city => {
        const cityName = city.name || city.cityName;
        const country = city.country;
        const state = city.state ? `, ${city.state}` : '';

        html += `
            <div class="list-group-item list-group-item-action p-2"
                 style="cursor: pointer;"
                 onclick="selectCity('${cityName}', '${country}', ${city.lat}, ${city.lon})">
                <div class="d-flex justify-content-between align-items-center">
                    <div>
                        <i class="fas fa-map-marker-alt text-muted me-2"></i>
                        <span class="fw-medium">${cityName}</span>
                        <small class="text-muted">${state} ${country}</small>
                    </div>
                </div>
            </div>
        `;
    });

    html += '</div>';
    resultsDiv.innerHTML = html;
}

// Select city and navigate to weather page
function selectCity(cityName, country, lat, lon) {
    window.location.href = `/dashboard/weather/${encodeURIComponent(cityName)}`;
}


// Get user's current location
function getCurrentLocation() {
    if (!navigator.geolocation) {
        showToast('Error', 'Geolocation is not supported by this browser', 'error');
        return;
    }

    showToast('Info', 'Getting your location...', 'info');

    navigator.geolocation.getCurrentPosition(
        function(position) {
            const lat = position.coords.latitude;
            const lon = position.coords.longitude;

            // Get weather by coordinates
            fetch(`/api/weather/coordinates?lat=${lat}&lon=${lon}`)
                .then(response => response.json())
                .then(data => {
                    if (data.error) {
                        throw new Error(data.error);
                    }
                    const cityName = data.name;
                    window.location.href = `/dashboard/weather/${encodeURIComponent(cityName)}`;
                })
                .catch(error => {
                    console.error('Error getting weather by location:', error);
                    showToast('Error', 'Failed to get weather for your location', 'error');
                });
        },
        function(error) {
            let message = 'Failed to get your location';
            switch(error.code) {
                case error.PERMISSION_DENIED:
                    message = 'Location access denied by user';
                    break;
                case error.POSITION_UNAVAILABLE:
                    message = 'Location information is unavailable';
                    break;
                case error.TIMEOUT:
                    message = 'Location request timed out';
                    break;
            }
            showToast('Error', message, 'error');
        },
        {
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 300000 // 5 minutes
        }
    );
}

// Weather comparison functionality (for compare page)
function addCityToComparison(cityName, country) {
    const comparisonList = document.getElementById('comparisonCities');
    if (!comparisonList) return;

    // Check if already added
    const existing = comparisonList.querySelector(`[data-city="${cityName}"]`);
    if (existing) {
        showToast('Warning', `${cityName} is already in comparison`, 'warning');
        return;
    }

    // Check limit (max 4 cities)
    const currentCities = comparisonList.children.length;
    if (currentCities >= 4) {
        showToast('Warning', 'Maximum 4 cities can be compared', 'warning');
        return;
    }

    // Fetch weather data and add to comparison
    fetch(`/api/weather/current/${encodeURIComponent(cityName)}`)
        .then(response => response.json())
        .then(data => {
            if (data.error) {
                throw new Error(data.error);
            }
            addCityCard(data, comparisonList);
            showToast('Success', `${cityName} added to comparison`, 'success');
        })
        .catch(error => {
            console.error('Error fetching weather:', error);
            showToast('Error', `Failed to get weather for ${cityName}`, 'error');
        });
}

// Add city card to comparison
function addCityCard(weatherData, container) {
    const cityCard = document.createElement('div');
    cityCard.className = 'col-md-6 col-lg-3 mb-3';
    cityCard.setAttribute('data-city', weatherData.name);

    cityCard.innerHTML = `
        <div class="card weather-card h-100">
            <div class="card-body text-center">
                <div class="d-flex justify-content-between align-items-start mb-2">
                    <h6 class="card-title mb-0">${weatherData.name}</h6>
                    <button class="btn btn-sm btn-outline-danger"
                            onclick="removeCityFromComparison('${weatherData.name}')">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="weather-icon mb-2">
                    <i class="fas fa-sun text-warning" style="font-size: 2.5rem;"></i>
                </div>
                <h4 class="mb-2">${Math.round(weatherData.main.temp)}°C</h4>
                <p class="text-muted small mb-2">${weatherData.weather[0].description}</p>
                <div class="row text-center mt-3">
                    <div class="col-12 mb-1">
                        <small class="text-muted">Humidity: ${weatherData.main.humidity}%</small>
                    </div>
                    <div class="col-12 mb-1">
                        <small class="text-muted">Wind: ${weatherData.wind.speed} km/h</small>
                    </div>
                    <div class="col-12">
                        <small class="text-muted">Pressure: ${weatherData.main.pressure} hPa</small>
                    </div>
                </div>
            </div>
        </div>
    `;

    container.appendChild(cityCard);
}

// Remove city from comparison
function removeCityFromComparison(cityName) {
    const cityCard = document.querySelector(`[data-city="${cityName}"]`);
    if (cityCard) {
        cityCard.remove();
        showToast('Success', `${cityName} removed from comparison`, 'success');
    }
}

// Clear all cities from comparison
function clearAllComparison() {
    const comparisonList = document.getElementById('comparisonCities');
    if (comparisonList && comparisonList.children.length > 0) {
        if (confirm('Remove all cities from comparison?')) {
            comparisonList.innerHTML = '';
            showToast('Success', 'All cities removed from comparison', 'success');
        }
    }
}

// Toast notification system
function showToast(title, message, type = 'info') {
    // Remove existing toasts
    const existingToasts = document.querySelectorAll('.toast-notification');
    existingToasts.forEach(toast => toast.remove());

    // Create toast container if it doesn't exist
    let toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) {
        toastContainer = document.createElement('div');
        toastContainer.id = 'toastContainer';
        toastContainer.className = 'position-fixed top-0 end-0 p-3';
        toastContainer.style.zIndex = '1050';
        document.body.appendChild(toastContainer);
    }

    // Create toast
    const toast = document.createElement('div');
    toast.className = `toast toast-notification show align-items-center text-white bg-${getBootstrapClass(type)} border-0`;
    toast.setAttribute('role', 'alert');

    toast.innerHTML = `
        <div class="d-flex">
            <div class="toast-body">
                <strong>${title}:</strong> ${message}
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto"
                    onclick="this.closest('.toast').remove()"></button>
        </div>
    `;

    toastContainer.appendChild(toast);

    // Auto remove after 4 seconds
    setTimeout(() => {
        if (toast.parentNode) {
            toast.remove();
        }
    }, 4000);
}

// Get Bootstrap class for toast type
function getBootstrapClass(type) {
    switch(type) {
        case 'success': return 'success';
        case 'error': return 'danger';
        case 'warning': return 'warning';
        case 'info': return 'info';
        default: return 'primary';
    }
}

// Format date for display
function formatDate(timestamp) {
    const date = new Date(timestamp * 1000);
    return date.toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric'
    });
}

// Format time for display
function formatTime(timestamp) {
    const date = new Date(timestamp * 1000);
    return date.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit'
    });
}

// Get weather icon based on weather condition
function getWeatherIcon(iconCode, description) {
    const iconMap = {
        '01d': 'fas fa-sun text-warning',
        '01n': 'fas fa-moon text-secondary',
        '02d': 'fas fa-cloud-sun text-warning',
        '02n': 'fas fa-cloud-moon text-secondary',
        '03d': 'fas fa-cloud text-secondary',
        '03n': 'fas fa-cloud text-secondary',
        '04d': 'fas fa-cloud text-dark',
        '04n': 'fas fa-cloud text-dark',
        '09d': 'fas fa-cloud-rain text-primary',
        '09n': 'fas fa-cloud-rain text-primary',
        '10d': 'fas fa-cloud-sun-rain text-primary',
        '10n': 'fas fa-cloud-moon-rain text-primary',
        '11d': 'fas fa-bolt text-warning',
        '11n': 'fas fa-bolt text-warning',
        '13d': 'fas fa-snowflake text-info',
        '13n': 'fas fa-snowflake text-info',
        '50d': 'fas fa-smog text-secondary',
        '50n': 'fas fa-smog text-secondary'
    };

    return iconMap[iconCode] || 'fas fa-cloud text-secondary';
}

// Initialize charts (if Chart.js is available)
function initializeCharts() {
    if (typeof Chart === 'undefined') {
        console.log('Chart.js not loaded');
        return;
    }

    // Temperature chart
    const tempCtx = document.getElementById('temperatureChart');
    if (tempCtx) {
        createTemperatureChart(tempCtx);
    }

    // Humidity chart
    const humidityCtx = document.getElementById('humidityChart');
    if (humidityCtx) {
        createHumidityChart(humidityCtx);
    }
}

// Create temperature chart
function createTemperatureChart(ctx) {
    // This would be populated with actual forecast data
    const data = {
        labels: ['Now', '3h', '6h', '9h', '12h', '15h', '18h', '21h'],
        datasets: [{
            label: 'Temperature (°C)',
            data: [25, 24, 23, 22, 24, 26, 28, 27],
            borderColor: 'rgb(25, 118, 210)',
            backgroundColor: 'rgba(25, 118, 210, 0.1)',
            tension: 0.4,
            fill: true
        }]
    };

    new Chart(ctx, {
        type: 'line',
        data: data,
        options: {
            responsive: true,
            plugins: {
                legend: {
                    display: false
                }
            },
            scales: {
                y: {
                    beginAtZero: false
                }
            }
        }
    });
}

// Create humidity chart
function createHumidityChart(ctx) {
    const data = {
        labels: ['Now', '3h', '6h', '9h', '12h', '15h', '18h', '21h'],
        datasets: [{
            label: 'Humidity (%)',
            data: [65, 68, 70, 72, 69, 64, 62, 66],
            backgroundColor: 'rgba(54, 162, 235, 0.8)',
            borderColor: 'rgba(54, 162, 235, 1)',
            borderWidth: 1
        }]
    };

    new Chart(ctx, {
        type: 'bar',
        data: data,
        options: {
            responsive: true,
            plugins: {
                legend: {
                    display: false
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    max: 100
                }
            }
        }
    });
}

// Utility function to debounce function calls
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// === OpenStreetMap (Leaflet.js) Map Modal Integration ===
let mapModalInstance, map, marker;

function openMapModal(forCompare = false) {
    // Create modal if not present
    let modal = document.getElementById('mapModal');
    if (!modal) {
        // Dynamically create modal if not present (for JS-only pages)
        const modalHtml = `
        <div class="modal fade" id="mapModal" tabindex="-1" aria-labelledby="mapModalLabel" aria-hidden="true">
          <div class="modal-dialog modal-lg modal-dialog-centered">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title" id="mapModalLabel">Select Location on Map</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
              </div>
              <div class="modal-body" style="height: 500px;">
                <div id="leafletMap" style="height: 100%; width: 100%;"></div>
              </div>
            </div>
          </div>
        </div>`;
        document.body.insertAdjacentHTML('beforeend', modalHtml);
        modal = document.getElementById('mapModal');
    }
    mapModalInstance = new bootstrap.Modal(modal);
    mapModalInstance.show();
    setTimeout(() => {
        if (!map) {
            map = L.map('leafletMap').setView([20, 0], 2);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '© OpenStreetMap contributors'
            }).addTo(map);
            map.on('click', (e) => onMapClick(e, forCompare));
        } else {
            map.invalidateSize();
        }
    }, 300);
}

function onMapClick(e, forCompare = false) {
    if (marker) {
        marker.setLatLng(e.latlng);
    } else {
        marker = L.marker(e.latlng).addTo(map);
    }
    fetch(`https://nominatim.openstreetmap.org/reverse?lat=${e.latlng.lat}&lon=${e.latlng.lng}&format=json`)
        .then(res => res.json())
        .then(data => {
            let city = data.address.city || data.address.town || data.address.village || data.address.state || '';
            let country = data.address.country || '';
            if (city) {
                if (forCompare && typeof addCityToComparison === 'function') {
                    addCityToComparison(city, country);
                } else {
                    window.location.href = `/dashboard/weather/${encodeURIComponent(city)}`;
                }
                if (mapModalInstance) mapModalInstance.hide();
            } else {
                alert('Could not determine city. Please try another location.');
            }
        })
        .catch(() => alert('Failed to get location details.'));
}

// Initialize charts when document is ready
document.addEventListener('DOMContentLoaded', function() {
    setTimeout(initializeCharts, 100);
});