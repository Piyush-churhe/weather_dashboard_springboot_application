# 🌤️ Weather Dashboard

A comprehensive Spring Boot web application that provides real-time weather information, detailed forecasts, and weather comparison tools with a modern, responsive user interface.

## ✨ Features

### 🌍 Weather Information
- **Real-time Weather Data** - Current weather conditions for any city worldwide
- **48-Hour Detailed Forecasts** - Hourly weather predictions with interactive charts (Premium feature)
- **Weather Alerts** - Automatic alerts for severe weather conditions
- **Multiple Location Support** - Track weather for multiple cities simultaneously

### 👥 User Management
- **Local Authentication** - Username/password registration and login
- **OAuth2 Integration** - Sign in with Google or GitHub
- **User Roles** - USER, PREMIUM, and ADMIN role management
- **Password Reset** - Email-based password recovery system

### ⚙️ Customization
- **User Preferences** - Temperature units (Celsius/Fahrenheit), wind speed units, time format
- **Theme Support** - Light/dark mode (configurable)
- **Personalized Dashboard** - Customized weather display based on user preferences

### 🔧 Advanced Features
- **Weather Comparison** - Side-by-side comparison of up to 4 cities
- **Interactive Maps** - Click-to-select location using OpenStreetMap
- **Admin Panel** - User management and role assignment
- **Responsive Design** - Mobile-friendly interface with Bootstrap 5
- **Charts & Visualization** - Weather trends with Chart.js integration

## 🛠️ Technology Stack

### Backend
- **Spring Boot 3.x** - Main framework
- **Spring Security** - Authentication and authorization
- **Spring Data MongoDB** - Database integration
- **Spring Web** - RESTful APIs and MVC
- **OAuth2 Client** - Social authentication

### Frontend
- **Thymeleaf** - Server-side templating
- **Bootstrap 5.3** - Responsive UI framework
- **Chart.js** - Data visualization
- **Font Awesome** - Icons
- **Leaflet.js** - Interactive maps

### Database & External APIs
- **MongoDB Atlas** - Cloud database
- **OpenWeatherMap API** - Weather data provider
- **JavaMail** - Email services

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- MongoDB Atlas account (or local MongoDB)
- OpenWeatherMap API key

### 1. Clone the Repository
```bash
git clone https://github.com/Piyush-churhe/weather-dashboard.git
cd weather-dashboard
```

### 2. Configuration
Create or update `src/main/resources/application.properties`:

```properties
# Application Configuration
spring.application.name=weather-dashboard
spring.profiles.active=production

# MongoDB Configuration
spring.data.mongodb.uri=${MONGODB_URI:your_mongodb_connection_string}

# Server Configuration
server.port=${PORT:8080}

# Weather API Configuration
weather.api.key=${WEATHER_API_KEY:your_openweathermap_api_key}
weather.api.base-url=https://api.openweathermap.org/data/2.5
weather.api.geocoding-url=http://api.openweathermap.org/geo/1.0

# OAuth2 Configuration (Optional)
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID:your_google_client_id}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET:your_google_client_secret}
spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID:your_github_client_id}
spring.security.oauth2.client.registration.github.client-secret=${GITHUB_CLIENT_SECRET:your_github_client_secret}

# Email Configuration (Optional)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${EMAIL_USERNAME:your_email@gmail.com}
spring.mail.password=${EMAIL_PASSWORD:your_app_password}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### 3. Environment Variables
Set the following environment variables:

```bash
export MONGODB_URI="your_mongodb_connection_string"
export WEATHER_API_KEY="your_openweathermap_api_key"
export GOOGLE_CLIENT_ID="your_google_oauth_client_id"  # Optional
export GOOGLE_CLIENT_SECRET="your_google_oauth_secret"  # Optional
export GITHUB_CLIENT_ID="your_github_oauth_client_id"  # Optional
export GITHUB_CLIENT_SECRET="your_github_oauth_secret"  # Optional
```

### 4. Run the Application
```bash
mvn spring-boot:run
```

The application will be available at `http://localhost:8080`

## 🔑 API Setup

### OpenWeatherMap API
1. Sign up at [OpenWeatherMap](https://openweathermap.org/api)
2. Get your free API key
3. Set the `WEATHER_API_KEY` environment variable

### OAuth2 Setup (Optional)

#### Google OAuth2
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable Google+ API
4. Create OAuth2 credentials
5. Add `http://localhost:8080/login/oauth2/code/google` to redirect URIs

#### GitHub OAuth2
1. Go to GitHub Settings → Developer settings → OAuth Apps
2. Create a new OAuth App
3. Set Authorization callback URL to `http://localhost:8080/login/oauth2/code/github`

## 📱 Usage

### Default Admin Account
- **Username:** `admin`
- **Password:** `admin123`
- **Roles:** ADMIN, PREMIUM

### User Roles
- **USER** - Basic weather information and city comparison
- **PREMIUM** - Access to detailed forecasts, weather alerts, and advanced features
- **ADMIN** - Full access plus user management capabilities

### Premium Upgrade
Users can upgrade to Premium using the promo code: `PREMIUM2024`

## 🏗️ Project Structure

```
src/
├── main/
│   ├── java/com/example/weatherdashboard/
│   │   ├── config/           # Security, web, and password encoder configuration
│   │   ├── controller/       # Web controllers and REST APIs
│   │   ├── model/           # Entity classes (User, UserPreferences)
│   │   ├── repository/      # Data repositories
│   │   ├── service/         # Business logic services
│   │   └── util/           # Utility classes (temperature conversion)
│   └── resources/
│       ├── static/          # CSS, JS, and static assets
│       └── templates/       # Thymeleaf templates
│           ├── auth/        # Authentication pages
│           └── dashboard/   # Main application pages
```

## 🌟 Key Features Walkthrough

### Weather Dashboard
- Search for any city worldwide
- View current weather conditions
- Access quick action buttons for forecasts and comparisons

### City Comparison
- Add up to 4 cities for side-by-side comparison
- Interactive map selection
- Detailed comparison table with all weather metrics

### Detailed Forecasts (Premium)
- 48-hour hourly forecasts
- Interactive charts showing temperature and precipitation trends
- Weather alerts and warnings

### Admin Panel
- User management interface
- Role assignment and modification
- User activity monitoring

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 🙏 Acknowledgments

- [OpenWeatherMap](https://openweathermap.org/) for weather data
- [Bootstrap](https://getbootstrap.com/) for UI components
- [Chart.js](https://www.chartjs.org/) for data visualization
- [Leaflet](https://leafletjs.com/) for interactive maps
- [Spring Boot](https://spring.io/projects/spring-boot) for the framework

---

<div align="center">
  Made with ❤️ using Spring Boot
</div>
